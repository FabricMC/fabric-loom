/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2018-2021 FabricMC
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

package net.fabricmc.loom.configuration.mods;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Manifest;

import com.google.gson.JsonObject;
import org.gradle.api.Project;
import org.objectweb.asm.commons.Remapper;

import net.fabricmc.accesswidener.AccessWidenerReader;
import net.fabricmc.accesswidener.AccessWidenerRemapper;
import net.fabricmc.accesswidener.AccessWidenerWriter;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.RemappedConfigurationEntry;
import net.fabricmc.loom.configuration.processors.dependency.ModDependencyInfo;
import net.fabricmc.loom.configuration.providers.mappings.MappingsProviderImpl;
import net.fabricmc.loom.task.RemapJarTask;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.TinyRemapperHelper;
import net.fabricmc.loom.util.ZipUtils;
import net.fabricmc.loom.util.kotlin.KotlinClasspathService;
import net.fabricmc.loom.util.kotlin.KotlinRemapperClassloader;
import net.fabricmc.tinyremapper.InputTag;
import net.fabricmc.tinyremapper.NonClassCopyMode;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;

public class ModProcessor {
	private static final String fromM = MappingsNamespace.INTERMEDIARY.toString();
	private static final String toM = MappingsNamespace.NAMED.toString();

	private final Project project;

	public ModProcessor(Project project) {
		this.project = project;
	}

	public void processMods(List<ModDependencyInfo> processList) throws IOException {
		ArrayList<ModDependencyInfo> remapList = new ArrayList<>();

		for (ModDependencyInfo info : processList) {
			if (info.requiresRemapping()) {
				project.getLogger().debug("{} requires remapping", info.getInputFile());
				Files.deleteIfExists(info.getRemappedOutput().toPath());

				remapList.add(info);
			}
		}

		if (remapList.isEmpty()) {
			project.getLogger().debug("No mods to remap, skipping");
			return;
		}

		try {
			remapJars(remapList);
		} catch (Exception e) {
			project.getLogger().error("Failed to remap %d mods".formatted(remapList.size()), e);

			for (ModDependencyInfo info : remapList) {
				Files.deleteIfExists(info.getRemappedOutput().toPath());
			}

			throw e;
		}

		// Check all the mods we expect exist
		for (ModDependencyInfo info : processList) {
			if (!info.getRemappedOutput().exists()) {
				throw new RuntimeException("Failed to find remapped mod" + info);
			}
		}
	}

	private void stripNestedJars(File file) {
		// Strip out all contained jar info as we dont want loader to try and load the jars contained in dev.
		try {
			ZipUtils.transformJson(JsonObject.class, file.toPath(), Map.of("fabric.mod.json", json -> {
				json.remove("jars");
				return json;
			}));
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to strip nested jars from %s".formatted(file), e);
		}
	}

	/**
	 * Remap another mod's access widener from intermediary to named, so that loader can apply it in our dev-env.
	 */
	private byte[] remapAccessWidener(byte[] input, Remapper remapper) {
		int version = AccessWidenerReader.readVersion(input);

		AccessWidenerWriter writer = new AccessWidenerWriter(version);
		AccessWidenerRemapper awRemapper = new AccessWidenerRemapper(
				writer,
				remapper,
				MappingsNamespace.INTERMEDIARY.toString(),
				MappingsNamespace.NAMED.toString()
		);
		AccessWidenerReader reader = new AccessWidenerReader(awRemapper);
		reader.read(input);
		return writer.write();
	}

	private void remapJars(List<ModDependencyInfo> remapList) throws IOException {
		final LoomGradleExtension extension = LoomGradleExtension.get(project);
		final MappingsProviderImpl mappingsProvider = extension.getMappingsProvider();
		Path[] mcDeps = project.getConfigurations().getByName(Constants.Configurations.LOADER_DEPENDENCIES).getFiles()
				.stream().map(File::toPath).toArray(Path[]::new);

		project.getLogger().lifecycle(":remapping " + remapList.size() + " mods (TinyRemapper, " + fromM + " -> " + toM + ")");

		TinyRemapper.Builder builder = TinyRemapper.newRemapper()
				.withMappings(TinyRemapperHelper.create(mappingsProvider.getMappings(), fromM, toM, false))
				.renameInvalidLocals(false);

		final KotlinClasspathService kotlinClasspathService = KotlinClasspathService.getOrCreateIfRequired(project);
		KotlinRemapperClassloader kotlinRemapperClassloader = null;

		if (kotlinClasspathService != null) {
			kotlinRemapperClassloader = KotlinRemapperClassloader.create(kotlinClasspathService);
			builder.extension(kotlinRemapperClassloader.getTinyRemapperExtension());
		}

		final TinyRemapper remapper = builder.build();

		for (Path minecraftJar : extension.getMinecraftJars(MappingsNamespace.INTERMEDIARY)) {
			remapper.readClassPathAsync(minecraftJar);
		}

		remapper.readClassPathAsync(mcDeps);

		final Map<ModDependencyInfo, InputTag> tagMap = new HashMap<>();
		final Map<ModDependencyInfo, OutputConsumerPath> outputConsumerMap = new HashMap<>();
		final Map<ModDependencyInfo, byte[]> accessWidenerMap = new HashMap<>();

		for (RemappedConfigurationEntry entry : Constants.MOD_COMPILE_ENTRIES) {
			for (File inputFile : project.getConfigurations().getByName(entry.sourceConfiguration()).getFiles()) {
				if (remapList.stream().noneMatch(info -> info.getInputFile().equals(inputFile))) {
					project.getLogger().debug("Adding " + inputFile + " onto the remap classpath");

					remapper.readClassPathAsync(inputFile.toPath());
				}
			}
		}

		for (ModDependencyInfo info : remapList) {
			InputTag tag = remapper.createInputTag();

			project.getLogger().debug("Adding " + info.getInputFile() + " as a remap input");

			remapper.readInputsAsync(tag, info.getInputFile().toPath());
			tagMap.put(info, tag);
		}

		try {
			// Apply this in a second loop as we need to ensure all the inputs are on the classpath before remapping.
			for (ModDependencyInfo info : remapList) {
				try {
					OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(info.getRemappedOutput().toPath()).build();

					outputConsumer.addNonClassFiles(info.getInputFile().toPath(), NonClassCopyMode.FIX_META_INF, remapper);
					outputConsumerMap.put(info, outputConsumer);

					final ModDependencyInfo.AccessWidenerData accessWidenerData = info.getAccessWidenerData();

					if (accessWidenerData != null) {
						project.getLogger().debug("Remapping access widener in {}", info.getInputFile());
						byte[] remappedAw = remapAccessWidener(accessWidenerData.content(), remapper.getEnvironment().getRemapper());
						accessWidenerMap.put(info, remappedAw);
					}

					remapper.apply(outputConsumer, tagMap.get(info));
				} catch (Exception e) {
					throw new RuntimeException("Failed to remap: " + info.getRemappedNotation(), e);
				}
			}
		} finally {
			remapper.finish();

			if (kotlinRemapperClassloader != null) {
				kotlinRemapperClassloader.close();
			}
		}

		for (ModDependencyInfo info : remapList) {
			outputConsumerMap.get(info).close();
			byte[] accessWidener = accessWidenerMap.get(info);

			if (accessWidener != null) {
				assert info.getAccessWidenerData() != null;
				ZipUtils.replace(info.getRemappedOutput().toPath(), info.getAccessWidenerData().path(), accessWidener);
			}

			stripNestedJars(info.getRemappedOutput());
			remapJarManifestEntries(info.getRemappedOutput().toPath());

			info.finaliseRemapping();
		}
	}

	private void remapJarManifestEntries(Path jar) throws IOException {
		ZipUtils.transform(jar, Map.of(RemapJarTask.MANIFEST_PATH, bytes -> {
			var manifest = new Manifest(new ByteArrayInputStream(bytes));

			manifest.getMainAttributes().putValue(RemapJarTask.MANIFEST_NAMESPACE_KEY, toM);

			ByteArrayOutputStream out = new ByteArrayOutputStream();
			manifest.write(out);
			return out.toByteArray();
		}));
	}
}
