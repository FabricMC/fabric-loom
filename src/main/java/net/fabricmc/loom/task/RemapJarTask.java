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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;

import com.google.common.base.Suppliers;
import com.google.gson.JsonObject;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.accesswidener.AccessWidenerReader;
import net.fabricmc.accesswidener.AccessWidenerRemapper;
import net.fabricmc.accesswidener.AccessWidenerWriter;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.build.MixinRefmapHelper;
import net.fabricmc.loom.build.nesting.IncludedJarFactory;
import net.fabricmc.loom.build.nesting.JarNester;
import net.fabricmc.loom.configuration.accesswidener.AccessWidenerFile;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftSourceSets;
import net.fabricmc.loom.extension.MixinExtension;
import net.fabricmc.loom.task.service.TinyRemapperService;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.ExceptionUtil;
import net.fabricmc.loom.util.ModUtils;
import net.fabricmc.loom.util.Pair;
import net.fabricmc.loom.util.SidedClassVisitor;
import net.fabricmc.loom.util.ZipUtils;
import net.fabricmc.loom.util.service.UnsafeWorkQueueHelper;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;

public abstract class RemapJarTask extends AbstractRemapJarTask {
	@InputFiles
	public abstract ConfigurableFileCollection getNestedJars();

	@Input
	public abstract Property<Boolean> getAddNestedDependencies();

	private Supplier<TinyRemapperService> tinyRemapperService = Suppliers.memoize(() -> TinyRemapperService.getOrCreate(this));

	@Inject
	public RemapJarTask() {
		super();

		// Lazy provider because the configuration is not yet registered at this point
		getClasspath().from(getProject().provider(() -> {
			String sourceSetName = MinecraftSourceSets.get(getProject()).getCombinedSourceSetName();
			// Use copy to avoid inherited configuration (Minecraft libraries)
			return getProject().getConfigurations().getByName(sourceSetName).copy();
		}));
		getClasspath().from(getProject().getConfigurations().getByName("modCompileClasspath"));

		getAddNestedDependencies().convention(true).finalizeValueOnRead();

		Configuration includeConfiguration = getProject().getConfigurations().getByName(Constants.Configurations.INCLUDE);
		getNestedJars().from(new IncludedJarFactory(getProject()).getNestedJars(includeConfiguration));

		setupPreparationTask();
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
		final LoomGradleExtension extension = LoomGradleExtension.get(getProject());

		submitWork(RemapAction.class, params -> {
			if (getAddNestedDependencies().get()) {
				params.getNestedJars().from(getNestedJars());
			}

			params.getTinyRemapperBuildServiceUuid().set(UnsafeWorkQueueHelper.create(getProject(), tinyRemapperService.get()));
			params.getRemapClasspath().from(getClasspath());

			final boolean legacyMixin = extension.getMixin().getUseLegacyMixinAp().get();
			params.getUseMixinExtension().set(!legacyMixin);

			if (legacyMixin) {
				setupLegacyMixinRefmapRemapping(params);
			}
		});
	}

	private void setupLegacyMixinRefmapRemapping(RemapParams params) {
		final LoomGradleExtension extension = LoomGradleExtension.get(getProject());
		final MixinExtension mixinExtension = extension.getMixin();

		final JsonObject fabricModJson = ModUtils.getFabricModJson(getInputFile().getAsFile().get().toPath());

		if (fabricModJson == null) {
			getProject().getLogger().warn("Could not find fabric.mod.json file in: " + getInputFile().getAsFile().get().getName());
			return;
		}

		final Collection<String> allMixinConfigs = MixinRefmapHelper.getMixinConfigurationFiles(fabricModJson);

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

			params.getMixinData().add(new RemapParams.RefmapData(mixinConfigs, refmapName));
		}
	}

	public interface RemapParams extends AbstractRemapParams {
		ConfigurableFileCollection getNestedJars();
		ConfigurableFileCollection getRemapClasspath();

		Property<Boolean> getUseMixinExtension();

		record RefmapData(List<String> mixinConfigs, String refmapName) implements Serializable { }
		ListProperty<RefmapData> getMixinData();

		Property<String> getTinyRemapperBuildServiceUuid();
	}

	public abstract static class RemapAction extends AbstractRemapAction<RemapParams> {
		private static final Logger LOGGER = LoggerFactory.getLogger(RemapAction.class);

		private final TinyRemapperService tinyRemapperService;
		private TinyRemapper tinyRemapper;

		public RemapAction() {
			this.tinyRemapperService = UnsafeWorkQueueHelper.get(getParameters().getTinyRemapperBuildServiceUuid(), TinyRemapperService.class);
		}

		@Override
		public void execute() {
			try {
				LOGGER.info("Remapping {} to {}", inputFile, outputFile);

				tinyRemapper = tinyRemapperService.getTinyRemapperForRemapping();

				remap();

				if (getParameters().getClientOnlyEntries().isPresent()) {
					markClientOnlyClasses();
				}

				remapAccessWidener();
				addRefmaps();
				addNestedJars();
				modifyJarManifest();
				rewriteJar();

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

		private void remap() throws IOException {
			try (OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(outputFile).build()) {
				outputConsumer.addNonClassFiles(inputFile);
				tinyRemapper.apply(outputConsumer, tinyRemapperService.getOrCreateTag(inputFile));
			}
		}

		private void markClientOnlyClasses() throws IOException {
			final Stream<Pair<String, ZipUtils.UnsafeUnaryOperator<byte[]>>> tranformers = getParameters().getClientOnlyEntries().get().stream()
					.map(s -> new Pair<>(s,
							(ZipUtils.AsmClassOperator) classVisitor -> SidedClassVisitor.CLIENT.insertApplyVisitor(null, classVisitor)
					));

			ZipUtils.transform(outputFile, tranformers);
		}

		private void remapAccessWidener() throws IOException {
			final AccessWidenerFile accessWidenerFile = AccessWidenerFile.fromModJar(inputFile);

			if (accessWidenerFile == null) {
				return;
			}

			byte[] remapped = remapAccessWidener(accessWidenerFile.content());

			// Finally, replace the output with the remaped aw
			ZipUtils.replace(outputFile, accessWidenerFile.path(), remapped);
		}

		private byte[] remapAccessWidener(byte[] input) {
			int version = AccessWidenerReader.readVersion(input);

			AccessWidenerWriter writer = new AccessWidenerWriter(version);
			AccessWidenerRemapper remapper = new AccessWidenerRemapper(
					writer,
					tinyRemapper.getEnvironment().getRemapper(),
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

			for (RemapParams.RefmapData refmapData : getParameters().getMixinData().get()) {
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
	protected List<String> getClientOnlyEntries() {
		final SourceSet clientSourceSet = MinecraftSourceSets.Split.getClientSourceSet(getProject());

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

	@Internal
	public TinyRemapperService getTinyRemapperService() {
		return tinyRemapperService.get();
	}
}
