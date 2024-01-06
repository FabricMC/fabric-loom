/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021-2022 FabricMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.fabricmc.loom.task;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;

import com.google.gson.JsonObject;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.accesswidener.AccessWidenerReader;
import net.fabricmc.accesswidener.AccessWidenerRemapper;
import net.fabricmc.accesswidener.AccessWidenerWriter;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.build.nesting.IncludedJarFactory;
import net.fabricmc.loom.build.nesting.JarNester;
import net.fabricmc.loom.configuration.accesswidener.AccessWidenerFile;
import net.fabricmc.loom.extension.MixinExtension;
import net.fabricmc.loom.task.service.TinyRemapperService;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.ExceptionUtil;
import net.fabricmc.loom.util.Pair;
import net.fabricmc.loom.util.SidedClassVisitor;
import net.fabricmc.loom.util.ZipUtils;
import net.fabricmc.loom.util.fmj.FabricModJson;
import net.fabricmc.loom.util.fmj.FabricModJsonFactory;
import net.fabricmc.loom.util.service.JsonSerializableHolder;
import net.fabricmc.loom.util.service.ScopedSharedServiceManager;
import net.fabricmc.tinyremapper.OutputConsumerPath;

public abstract class RemapJarTask extends AbstractRemapJarTask {
	@InputFiles
	public abstract ConfigurableFileCollection getNestedJars();

	@Input
	public abstract Property<Boolean> getAddNestedDependencies();

	@Input
	@ApiStatus.Internal
	public abstract Property<Boolean> getUseMixinAP();

	@Input
	public abstract Property<JsonSerializableHolder<TinyRemapperService.Spec>> getSpec();

	@Input
	public abstract Property<JsonSerializableHolder<List<RefmapData>>> getRefmapData();

	@Inject
	public RemapJarTask() {
		super();
		getClasspath().from(getProject().getConfigurations().getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME));
		getAddNestedDependencies().convention(true).finalizeValueOnRead();

		Configuration includeConfiguration = getProject().getConfigurations().getByName(Constants.Configurations.INCLUDE);
		getNestedJars().from(new IncludedJarFactory(getProject()).getNestedJars(includeConfiguration));

		getUseMixinAP().set(LoomGradleExtension.get(getProject()).getMixin().getUseLegacyMixinAp());

		if (getLoomExtension().multiProjectOptimisation()) {
			setupPreparationTask();
		}

		getSpec().set(getProject().provider(() -> new JsonSerializableHolder<>(TinyRemapperService.create(this))));

		if (getUseMixinAP().get()) {
			getRefmapData().set(getProject().provider(this::buildRefmapDataHolder));
		}
	}

	private void setupPreparationTask() {
		PrepareJarRemapTask prepareJarTask = getProject().getTasks().create("prepare" + getName().substring(0, 1).toUpperCase() + getName().substring(1), PrepareJarRemapTask.class, this);

		dependsOn(prepareJarTask);
		mustRunAfter(prepareJarTask);

		getProject().getGradle().allprojects(project -> {
			project.getTasks().configureEach(task -> {
				if (task instanceof PrepareJarRemapTask otherTask) {
					// Ensure that all remap jars run after all prepare tasks
					mustRunAfter(otherTask);
				}
			});
		});
	}

	@TaskAction
	public void run() {
		submitWork(RemapAction.class, params -> {
			if (getAddNestedDependencies().get()) {
				params.getNestedJars().from(getNestedJars());
			}

			params.getTinyRemapperSpec().set(getSpec());
			params.getRemapClasspath().from(getClasspath());

			// TODO: fix me
			//params.getMultiProjectOptimisation().set(getLoomExtension().multiProjectOptimisation());
			params.getMultiProjectOptimisation().set(false);

			final boolean mixinAp = getUseMixinAP().get();
			params.getUseMixinExtension().set(!mixinAp);
			params.getMixinData().set(getRefmapData());
		});
	}

	private JsonSerializableHolder<List<RefmapData>> buildRefmapDataHolder() {
		final LoomGradleExtension extension = LoomGradleExtension.get(getProject());
		final MixinExtension mixinExtension = extension.getMixin();

		final FabricModJson fabricModJson = FabricModJsonFactory.createFromZipNullable(getInputFile().getAsFile().get().toPath());

		if (fabricModJson == null) {
			return null;
		}

		final Collection<String> allMixinConfigs = fabricModJson.getMixinConfigurations();

		var list = new ArrayList<RefmapData>();

		for (SourceSet sourceSet : mixinExtension.getMixinSourceSets()) {
			MixinExtension.MixinInformationContainer container = Objects.requireNonNull(
					MixinExtension.getMixinInformationContainer(sourceSet)
			);

			final List<String> rootPaths = getRootPaths(sourceSet.getResources().getSrcDirs());

			final String refmapName = container.refmapNameProvider().get();
			final List<String> mixinConfigs = container.sourceSet().getResources()
					.matching(container.mixinConfigPattern())
					.getFiles()
					.stream()
					.map(relativePath(rootPaths))
					.filter(allMixinConfigs::contains)
					.toList();

			list.add(new RefmapData(mixinConfigs, refmapName));
		}

		return new JsonSerializableHolder<>(Collections.unmodifiableList(list));
	}

	public interface RemapParams extends AbstractRemapParams {
		ConfigurableFileCollection getNestedJars();
		ConfigurableFileCollection getRemapClasspath();

		Property<Boolean> getUseMixinExtension();
		Property<Boolean> getMultiProjectOptimisation();

		Property<JsonSerializableHolder<List<RefmapData>>> getMixinData();

		Property<JsonSerializableHolder<TinyRemapperService.Spec>> getTinyRemapperSpec();
	}

	public abstract static class RemapAction extends AbstractRemapAction<RemapParams> {
		private static final Logger LOGGER = LoggerFactory.getLogger(RemapAction.class);

		@Override
		public void execute() {
			try (var serviceManager = new ScopedSharedServiceManager()) {
				final TinyRemapperService tinyRemapperService = serviceManager.getOrCreateService(getParameters().getTinyRemapperSpec().get().get());

				try {
					LOGGER.info("Remapping {} to {}", inputFile, outputFile);

					if (!getParameters().getMultiProjectOptimisation().get()) {
						prepare(tinyRemapperService);
					}

					remap(tinyRemapperService);

					if (getParameters().getClientOnlyEntries().isPresent()) {
						markClientOnlyClasses();
					}

					remapAccessWidener(tinyRemapperService);
					addRefmaps();
					addNestedJars();
					modifyJarManifest();
					rewriteJar();

					if (!getParameters().getMultiProjectOptimisation().get()) {
						tinyRemapperService.close();
					}

					LOGGER.debug("Finished remapping {}", inputFile);
				} catch (Exception e) {
					try {
						Files.deleteIfExists(outputFile);
					} catch (IOException ex) {
						LOGGER.error("Failed to delete output file", ex);
					}

					throw ExceptionUtil.createDescriptiveWrapper(RuntimeException::new, "Failed to remap", e);
				}
			}
		}

		private void prepare(TinyRemapperService tinyRemapperService) {
			final Path inputFile = getParameters().getInputFile().getAsFile().get().toPath();
			PrepareJarRemapTask.prepare(tinyRemapperService, inputFile);
		}

		private void remap(TinyRemapperService tinyRemapperService) throws IOException {
			try (OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(outputFile).build()) {
				outputConsumer.addNonClassFiles(inputFile);
				tinyRemapperService.getTinyRemapperForRemapping().apply(outputConsumer, tinyRemapperService.getOrCreateTag(inputFile));
			}
		}

		private void markClientOnlyClasses() throws IOException {
			final Stream<Pair<String, ZipUtils.UnsafeUnaryOperator<byte[]>>> tranformers = getParameters().getClientOnlyEntries().get().stream()
					.map(s -> new Pair<>(s,
							(ZipUtils.AsmClassOperator) classVisitor -> SidedClassVisitor.CLIENT.insertApplyVisitor(null, classVisitor)
					));

			ZipUtils.transform(outputFile, tranformers);
		}

		private void remapAccessWidener(TinyRemapperService tinyRemapperService) throws IOException {
			final AccessWidenerFile accessWidenerFile = AccessWidenerFile.fromModJar(inputFile);

			if (accessWidenerFile == null) {
				return;
			}

			byte[] remapped = remapAccessWidener(tinyRemapperService, accessWidenerFile.content());

			// Finally, replace the output with the remaped aw
			ZipUtils.replace(outputFile, accessWidenerFile.path(), remapped);
		}

		private byte[] remapAccessWidener(TinyRemapperService tinyRemapperService, byte[] input) {
			int version = AccessWidenerReader.readVersion(input);

			AccessWidenerWriter writer = new AccessWidenerWriter(version);
			AccessWidenerRemapper remapper = new AccessWidenerRemapper(
					writer,
					tinyRemapperService.getTinyRemapperForRemapping().getEnvironment().getRemapper(),
					getParameters().getSourceNamespace().get(),
					getParameters().getTargetNamespace().get()
			);
			AccessWidenerReader reader = new AccessWidenerReader(remapper);
			reader.read(input);

			return writer.write();
		}

		private void addNestedJars() {
			FileCollection nestedJars = getParameters().getNestedJars();

			if (nestedJars.isEmpty()) {
				LOGGER.info("No jars to nest");
				return;
			}

			JarNester.nestJars(nestedJars.getFiles(), outputFile.toFile(), LOGGER);
		}

		private void addRefmaps() throws IOException {
			if (getParameters().getUseMixinExtension().get()) {
				return;
			}

			for (RefmapData refmapData : getParameters().getMixinData().get().get()) {
				int transformed = ZipUtils.transformJson(JsonObject.class, outputFile, refmapData.mixinConfigs().stream().collect(Collectors.toMap(s -> s, s -> json -> {
					if (!json.has("refmap")) {
						json.addProperty("refmap", refmapData.refmapName());
					}

					return json;
				})));
			}
		}
	}

	@Override
	protected List<String> getClientOnlyEntries(SourceSet clientSourceSet) {
		final ConfigurableFileCollection output = getProject().getObjects().fileCollection();
		output.from(clientSourceSet.getOutput().getClassesDirs());
		output.from(clientSourceSet.getOutput().getResourcesDir());

		final List<String> rootPaths = new ArrayList<>();

		rootPaths.addAll(getRootPaths(clientSourceSet.getOutput().getClassesDirs().getFiles()));
		rootPaths.addAll(getRootPaths(Set.of(Objects.requireNonNull(clientSourceSet.getOutput().getResourcesDir()))));

		return output.getAsFileTree().getFiles().stream()
				.map(relativePath(rootPaths))
				.toList();
	}

	record RefmapData(List<String> mixinConfigs, String refmapName) implements Serializable { }
}
