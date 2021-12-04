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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import javax.inject.Inject;

import com.google.common.base.Preconditions;
import com.google.gson.JsonObject;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import org.gradle.jvm.tasks.Jar;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.zip.ZipUtil;
import org.zeroturnaround.zip.transform.StreamZipEntryTransformer;
import org.zeroturnaround.zip.transform.StringZipEntryTransformer;
import org.zeroturnaround.zip.transform.ZipEntryTransformerEntry;

import net.fabricmc.accesswidener.AccessWidenerReader;
import net.fabricmc.accesswidener.AccessWidenerRemapper;
import net.fabricmc.accesswidener.AccessWidenerWriter;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.build.MixinRefmapHelper;
import net.fabricmc.loom.build.nesting.JarNester;
import net.fabricmc.loom.configuration.accesswidener.AccessWidenerFile;
import net.fabricmc.loom.configuration.providers.mappings.MappingsProviderImpl;
import net.fabricmc.loom.extension.MixinExtension;
import net.fabricmc.loom.task.service.JarManifestService;
import net.fabricmc.loom.task.service.TinyRemapperMappingsService;
import net.fabricmc.loom.task.service.TinyRemapperService;
import net.fabricmc.loom.util.ZipReprocessorUtil;
import net.fabricmc.tinyremapper.InputTag;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;

public abstract class RemapJarTask extends Jar {
	@InputFile
	public abstract RegularFileProperty getInputFile();

	@InputFiles
	public abstract ConfigurableFileCollection getNestedJars();

	@InputFiles
	public abstract ConfigurableFileCollection getClasspath();

	@Input
	public abstract Property<String> getSourceNamespace();

	@Input
	public abstract Property<String> getTargetNamespace();

	@Inject
	protected abstract WorkerExecutor getWorkerExecutor();

	@Inject
	public RemapJarTask() {
		getSourceNamespace().convention(MappingsNamespace.NAMED.toString()).finalizeValueOnRead();
		getTargetNamespace().convention(MappingsNamespace.INTERMEDIARY.toString()).finalizeValueOnRead();
	}

	@TaskAction
	public void run() {
		final LoomGradleExtension extension = LoomGradleExtension.get(getProject());
		final WorkQueue workQueue = getWorkerExecutor().noIsolation();

		workQueue.submit(RemapAction.class, params -> {
			params.getInputFile().set(getInputFile());
			params.getOutputFile().set(getArchiveFile());
			params.getNestedJars().plus(getNestedJars());

			params.getRemapAccessWidener().set(extension.getAccessWidenerPath().isPresent());
			params.getSourceNamespace().set(getSourceNamespace());
			params.getTargetNamespace().set(getTargetNamespace());

			params.getArchivePreserveFileTimestamps().set(isPreserveFileTimestamps());
			params.getArchiveReproducibleFileOrder().set(isReproducibleFileOrder());

			params.getTinyRemapperBuildService().set(getTinyRemapperService());
			params.getJarManifestService().set(JarManifestService.get(getProject()));

			final boolean legacyMixin = extension.getMixin().getUseLegacyMixinAp().get();
			params.getAddRefmap().set(legacyMixin);

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

	private synchronized Provider<TinyRemapperService> getTinyRemapperService() {
		final LoomGradleExtension extension = LoomGradleExtension.get(getProject());
		final MappingsProviderImpl mappingsProvider = extension.getMappingsProvider();
		final String from = getSourceNamespace().get();
		final String to = getTargetNamespace().get();
		// This should give us what shared caches did before but for free, and safely.
		final String name = "remapJarService:%s:%s>%S".formatted(mappingsProvider.mappingsIdentifier(), from, to);

		return getProject().getGradle().getSharedServices().registerIfAbsent(name, TinyRemapperService.class, spec -> {
			spec.parameters(params -> {
				params.getClasspath().plus(getProject().getConfigurations().getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME));
				params.getMappings().add(TinyRemapperMappingsService.create(getProject(), mappingsProvider.tinyMappings.toFile(), from, to, false));

				final boolean legacyMixin = extension.getMixin().getUseLegacyMixinAp().get();
				params.getUseMixinExtension().set(!legacyMixin);

				if (legacyMixin) {
					// Add the mapping from the mixin AP
					for (File file : extension.getAllMixinMappings().getFiles()) {
						params.getMappings().add(TinyRemapperMappingsService.create(getProject(), file, from, to, false));
					}
				}
			});
		});
	}

	public interface RemapParams extends WorkParameters {
		RegularFileProperty getInputFile();
		RegularFileProperty getOutputFile();
		ConfigurableFileCollection getNestedJars();
		ConfigurableFileCollection getExtraClasspath();

		Property<Boolean> getRemapAccessWidener();
		Property<Boolean> getAddRefmap();
		Property<String> getSourceNamespace();
		Property<String> getTargetNamespace();

		record RefmapData(List<String> mixinConfigs, String refmapName) implements Serializable { }
		ListProperty<RefmapData> getMixinData();

		Property<Boolean> getArchivePreserveFileTimestamps();
		Property<Boolean> getArchiveReproducibleFileOrder();

		Property<TinyRemapperService> getTinyRemapperBuildService();
		Property<JarManifestService> getJarManifestService();
	}

	public abstract static class RemapAction implements WorkAction<RemapParams> {
		// TODO is this the correct way to get a logger here?
		private static final Logger LOGGER = LoggerFactory.getLogger(RemapAction.class);

		private final TinyRemapper tinyRemapper;
		private final Path inputFile;
		private final Path outputFile;

		@Inject
		public RemapAction() {
			tinyRemapper = getParameters().getTinyRemapperBuildService().get().getTinyRemapper();
			inputFile = getParameters().getInputFile().getAsFile().get().toPath();
			outputFile = getParameters().getOutputFile().getAsFile().get().toPath();
		}

		@Override
		public void execute() {
			try {
				remap();
				remapAccessWidener();
				addRefmaps();
				addNestedJars();
				modifyJarManifest();
				rewriteJar();
			} catch (Exception e) {
				try {
					Files.deleteIfExists(outputFile);
				} catch (IOException ex) {
					LOGGER.error("Failed to delete output file", ex);
				}

				throw e;
			}
		}

		private void remap() {
			final InputTag inputTag = tinyRemapper.createInputTag();

			for (File file : getParameters().getExtraClasspath().getFiles()) {
				tinyRemapper.readClassPathAsync(file.toPath());
			}

			tinyRemapper.readInputsAsync(inputTag, inputFile);

			try (OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(outputFile).build()) {
				outputConsumer.addNonClassFiles(inputFile);
				tinyRemapper.apply(outputConsumer, inputTag);
			} catch (IOException e) {
				throw new RuntimeException("Failed to remap " + inputFile.getFileName().toString(), e);
			}
		}

		private void remapAccessWidener() {
			if (!getParameters().getRemapAccessWidener().get()) {
				return;
			}

			final AccessWidenerFile accessWidenerFile = AccessWidenerFile.fromModJar(inputFile);
			int version = AccessWidenerReader.readVersion(accessWidenerFile.content());

			AccessWidenerWriter writer = new AccessWidenerWriter(version);
			AccessWidenerRemapper remapper = new AccessWidenerRemapper(
					writer,
					tinyRemapper.getEnvironment().getRemapper(),
					MappingsNamespace.NAMED.toString(),
					MappingsNamespace.INTERMEDIARY.toString()
			);

			AccessWidenerReader reader = new AccessWidenerReader(remapper);
			reader.read(accessWidenerFile.content());

			// Finally, replace the output with the remaped aw
			try {
				ZipUtils.replace(outputFile.toFile(), accessWidenerFile.path(), writer.write());
			} catch (IOException e) {
				throw new UncheckedIOException("Failed to replace access widener in output jar", e);
			}
		}

		private void addNestedJars() {
			FileCollection nestedJars = getParameters().getNestedJars();

			if (nestedJars.isEmpty()) {
				LOGGER.info("No jars to nest");
				return;
			}

			JarNester.nestJars(nestedJars.getFiles(), inputFile.toFile(), LOGGER);
		}

		private void modifyJarManifest() {
			// Add data to the manifest
			boolean transformed = ZipUtil.transformEntries(outputFile.toFile(), new ZipEntryTransformerEntry[]{
					new ZipEntryTransformerEntry("META-INF/MANIFEST.MF", new StreamZipEntryTransformer() {
						@Override
						protected void transform(ZipEntry zipEntry, InputStream in, OutputStream out) throws IOException {
							var manifest = new Manifest(in);

							getParameters().getJarManifestService().get().apply(manifest);

							manifest.getMainAttributes().putValue("Fabric-Mapping-Namespace", getParameters().getTargetNamespace().get());
							manifest.write(out);
						}
					})
			});
			Preconditions.checkArgument(transformed, "Failed to transform jar manifest");
		}

		private void addRefmaps() {
			if (!getParameters().getAddRefmap().get()) {
				return;
			}

			for (RemapParams.RefmapData refmapData : getParameters().getMixinData().get()) {
				boolean transformed = ZipUtil.transformEntries(outputFile.toFile(), refmapData.mixinConfigs().stream().map(f -> new ZipEntryTransformerEntry(f, new StringZipEntryTransformer("UTF-8") {
					@Override
					protected String transform(ZipEntry zipEntry, String input) {
						JsonObject json = LoomGradlePlugin.GSON.fromJson(input, JsonObject.class);

						if (!json.has("refmap")) {
							json.addProperty("refmap", refmapData.refmapName());
						}

						return LoomGradlePlugin.GSON.toJson(json);
					}
				})).toArray(ZipEntryTransformerEntry[]::new));
			}
		}

		private void rewriteJar() {
			final boolean isReproducibleFileOrder = getParameters().getArchiveReproducibleFileOrder().get();
			final boolean isPreserveFileTimestamps = getParameters().getArchivePreserveFileTimestamps().get();

			if (isReproducibleFileOrder || !isPreserveFileTimestamps) {
				try {
					ZipReprocessorUtil.reprocessZip(outputFile.toFile(), isReproducibleFileOrder, isPreserveFileTimestamps);
				} catch (IOException e) {
					throw new RuntimeException("Failed to re-process jar", e);
				}
			}
		}
	}
}
