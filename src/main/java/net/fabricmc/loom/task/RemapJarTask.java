/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021 FabricMC
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

import javax.inject.Inject;

import com.google.common.base.Preconditions;
import com.google.gson.JsonObject;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import org.objectweb.asm.commons.Remapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.accesswidener.AccessWidenerReader;
import net.fabricmc.accesswidener.AccessWidenerRemapper;
import net.fabricmc.accesswidener.AccessWidenerWriter;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.build.MixinRefmapHelper;
import net.fabricmc.loom.build.nesting.JarNester;
import net.fabricmc.loom.configuration.accesswidener.AccessWidenerFile;
import net.fabricmc.loom.extension.MixinExtension;
import net.fabricmc.loom.task.service.JarManifestService;
import net.fabricmc.loom.task.service.MappingsService;
import net.fabricmc.loom.util.ZipUtils;
import net.fabricmc.tinyremapper.InputTag;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;

public abstract class RemapJarTask extends AbstractRemapJarTask {
	private static final String MANIFEST_PATH = "META-INF/MANIFEST.MF";

	@InputFiles
	public abstract ConfigurableFileCollection getNestedJars();

	@Input
	public abstract Property<Boolean> getAddNestedDependencies();

	@Inject
	public RemapJarTask() {
		super();

		getClasspath().plus(getProject().getConfigurations().getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME));
		getAddNestedDependencies().convention(true).finalizeValueOnRead();
	}

	@TaskAction
	public void run() {
		final LoomGradleExtension extension = LoomGradleExtension.get(getProject());

		submitWork(RemapAction.class, params -> {
			if (getAddNestedDependencies().get()) {
				params.getNestedJars().plus(getNestedJars());
			}

			params.getJarManifestService().set(JarManifestService.get(getProject()));
			params.getRemapClasspath().from(getClasspath());
			params.getMappings().add(MappingsService.createDefault(getProject(), getSourceNamespace().get(), getTargetNamespace().get()));

			final boolean legacyMixin = extension.getMixin().getUseLegacyMixinAp().get();
			params.getUseMixinExtension().set(!legacyMixin);

			if (legacyMixin) {
				// Add the mapping from the mixin AP
				for (File file : extension.getAllMixinMappings().getFiles()) {
					// TODO fix me, use the hash?
					String name = file.getAbsolutePath();
					params.getMappings().add(MappingsService.create(getProject(), name, file, getSourceNamespace().get(), getTargetNamespace().get(), false));
				}
			}

			if (legacyMixin) {
				setupLegacyMixinRefmapRemapping(params);
			}
		});
	}

	private void setupLegacyMixinRefmapRemapping(RemapParams params) {
		final LoomGradleExtension extension = LoomGradleExtension.get(getProject());
		final MixinExtension mixinExtension = extension.getMixin();

		final JsonObject fabricModJson = MixinRefmapHelper.readFabricModJson(getInputFile().getAsFile().get());

		if (fabricModJson == null) {
			getProject().getLogger().warn("Could not find fabric.mod.json file in: " + getInputFile().getAsFile().get().getName());
			return;
		}

		final Collection<String> allMixinConfigs = MixinRefmapHelper.getMixinConfigurationFiles(fabricModJson);

		for (SourceSet sourceSet : mixinExtension.getMixinSourceSets()) {
			MixinExtension.MixinInformationContainer container = Objects.requireNonNull(
					MixinExtension.getMixinInformationContainer(sourceSet)
			);

			final String refmapName = container.refmapNameProvider().get();
			final List<String> mixinConfigs = container.sourceSet().getResources()
					.matching(container.mixinConfigPattern())
					.getFiles()
					.stream()
					.map(File::getName)
					.filter(allMixinConfigs::contains)
					.toList();

			params.getMixinData().add(new RemapParams.RefmapData(mixinConfigs, refmapName));
		}
	}

	public interface RemapParams extends AbstractRemapParams {
		ConfigurableFileCollection getNestedJars();
		ConfigurableFileCollection getRemapClasspath();
		ListProperty<Provider<MappingsService>> getMappings();

		Property<Boolean> getUseMixinExtension();

		record RefmapData(List<String> mixinConfigs, String refmapName) implements Serializable { }
		ListProperty<RefmapData> getMixinData();

		Property<JarManifestService> getJarManifestService();
	}

	public abstract static class RemapAction extends AbstractRemapAction<RemapParams> {
		private static final Logger LOGGER = LoggerFactory.getLogger(RemapAction.class);

		private TinyRemapper tinyRemapper;

		@Override
		public void execute() {
			try {
				LOGGER.info("Remapping {} to {}", inputFile, outputFile);

				tinyRemapper = createTinyRemapper();

				remap();
				remapAccessWidener();
				addRefmaps();
				addNestedJars();
				modifyJarManifest();
				rewriteJar();

				tinyRemapper.finish();
				tinyRemapper = null;

				LOGGER.debug("Finished remapping {}", inputFile);
			} catch (Exception e) {
				try {
					Files.deleteIfExists(outputFile);
				} catch (IOException ex) {
					LOGGER.error("Failed to delete output file", ex);
				}

				throw new RuntimeException("Failed to remap", e);
			}
		}

		private void remap() throws IOException {
			final InputTag inputTag = tinyRemapper.createInputTag();

			tinyRemapper.readInputsAsync(inputTag, inputFile);

			try (OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(outputFile).build()) {
				outputConsumer.addNonClassFiles(inputFile);
				tinyRemapper.apply(outputConsumer, inputTag);
			}
		}

		private void remapAccessWidener() throws IOException {
			final AccessWidenerFile accessWidenerFile = AccessWidenerFile.fromModJar(inputFile);

			if (accessWidenerFile == null) {
				return;
			}

			byte[] remapped = remapAccessWidener(accessWidenerFile.content(), tinyRemapper.getEnvironment().getRemapper(), MappingsNamespace.INTERMEDIARY.toString());

			// Finally, replace the output with the remaped aw
			ZipUtils.replace(outputFile, accessWidenerFile.path(), remapped);
		}

		private static byte[] remapAccessWidener(byte[] input, Remapper asmRemapper, String targetNamespace) {
			int version = AccessWidenerReader.readVersion(input);

			AccessWidenerWriter writer = new AccessWidenerWriter(version);
			AccessWidenerRemapper remapper = new AccessWidenerRemapper(
					writer,
					asmRemapper,
					MappingsNamespace.NAMED.toString(),
					targetNamespace
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

			JarNester.nestJars(nestedJars.getFiles(), inputFile.toFile(), LOGGER);
		}

		private void modifyJarManifest() throws IOException {
			int count = ZipUtils.transform(outputFile, Map.of(MANIFEST_PATH, bytes -> {
				var manifest = new Manifest(new ByteArrayInputStream(bytes));

				getParameters().getJarManifestService().get().apply(manifest);
				manifest.getMainAttributes().putValue("Fabric-Mapping-Namespace", getParameters().getTargetNamespace().get());

				ByteArrayOutputStream out = new ByteArrayOutputStream();
				manifest.write(out);
				return out.toByteArray();
			}));

			Preconditions.checkState(count > 0, "Did not transform any jar manifest");
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

				assert transformed == refmapData.mixinConfigs().size();
			}
		}

		private TinyRemapper createTinyRemapper() {
			TinyRemapper.Builder builder = TinyRemapper.newRemapper();

			for (Provider<MappingsService> provider : getParameters().getMappings().get()) {
				builder.withMappings(provider.get().getMappingsProvider());
			}

			if (getParameters().getUseMixinExtension().get()) {
				builder.extension(new net.fabricmc.tinyremapper.extension.mixin.MixinExtension());
			}

			TinyRemapper remapper = builder.build();

			// Apply classpath
			for (File file : getParameters().getRemapClasspath()) {
				remapper.readClassPathAsync(file.toPath());
			}

			return remapper;
		}
	}
}
