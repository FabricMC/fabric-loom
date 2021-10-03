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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

import com.google.gson.JsonObject;
import org.gradle.api.Project;
import org.objectweb.asm.commons.Remapper;

import net.fabricmc.accesswidener.AccessWidenerReader;
import net.fabricmc.accesswidener.AccessWidenerRemapper;
import net.fabricmc.accesswidener.AccessWidenerWriter;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.RemappedConfigurationEntry;
import net.fabricmc.loom.configuration.processors.dependency.ModDependencyInfo;
import net.fabricmc.loom.configuration.providers.mappings.MappingsProviderImpl;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftMappedProvider;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.TinyRemapperHelper;
import net.fabricmc.loom.util.ZipUtils;
import net.fabricmc.tinyremapper.InputTag;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;

public class ModProcessor {
	public static void processMods(Project project, List<ModDependencyInfo> processList) throws IOException {
		if (processList.stream().noneMatch(ModDependencyInfo::requiresRemapping)) {
			return;
		}

		ArrayList<ModDependencyInfo> remapList = new ArrayList<>();

		for (ModDependencyInfo info : processList) {
			if (info.requiresRemapping()) {
				if (info.getRemappedOutput().exists()) {
					info.getRemappedOutput().delete();
				}

				remapList.add(info);
			}
		}

		remapJars(project, processList);

		for (ModDependencyInfo info : processList) {
			if (!info.getRemappedOutput().exists()) {
				throw new RuntimeException("Failed to find remapped mod" + info);
			}
		}

		for (ModDependencyInfo info : remapList) {
			stripNestedJars(info.getRemappedOutput());
		}
	}

	private static void stripNestedJars(File file) {
		// Strip out all contained jar info as we dont want loader to try and load the jars contained in dev.
		ZipUtils.transformJson(JsonObject.class, file.toPath(), Map.of("fabric.mod.json", json -> {
			json.remove("jars");
			return json;
		}));
	}

	/**
	 * Remap another mod's access widener from intermediary to named, so that loader can apply it in our dev-env.
	 */
	private static byte[] remapAccessWidener(byte[] input, Remapper remapper) {
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

	private static void remapJars(Project project, List<ModDependencyInfo> processList) throws IOException {
		LoomGradleExtension extension = LoomGradleExtension.get(project);
		String fromM = MappingsNamespace.INTERMEDIARY.toString();
		String toM = MappingsNamespace.NAMED.toString();

		MinecraftMappedProvider mappedProvider = extension.getMinecraftMappedProvider();
		MappingsProviderImpl mappingsProvider = extension.getMappingsProvider();

		Path mc = mappedProvider.getIntermediaryJar().toPath();
		Path[] mcDeps = project.getConfigurations().getByName(Constants.Configurations.LOADER_DEPENDENCIES).getFiles()
				.stream().map(File::toPath).toArray(Path[]::new);

		List<ModDependencyInfo> remapList = processList.stream().filter(ModDependencyInfo::requiresRemapping).collect(Collectors.toList());

		project.getLogger().lifecycle(":remapping " + remapList.size() + " mods (TinyRemapper, " + fromM + " -> " + toM + ")");

		TinyRemapper remapper = TinyRemapper.newRemapper()
				.withMappings(TinyRemapperHelper.create(mappingsProvider.getMappings(), fromM, toM, false))
				.renameInvalidLocals(false)
				.build();

		remapper.readClassPathAsync(mc);
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

		// Apply this in a second loop as we need to ensure all the inputs are on the classpath before remapping.
		for (ModDependencyInfo info : remapList) {
			try {
				OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(info.getRemappedOutput().toPath()).build();

				outputConsumer.addNonClassFiles(info.getInputFile().toPath());
				outputConsumerMap.put(info, outputConsumer);
				String accessWidener = info.getAccessWidener();

				if (accessWidener != null) {
					accessWidenerMap.put(info, remapAccessWidener(ZipUtils.unpackStrict(info.inputFile.toPath(), accessWidener), remapper.getRemapper()));
				}

				remapper.apply(outputConsumer, tagMap.get(info));
			} catch (Exception e) {
				remapper.finish();
				Files.deleteIfExists(info.getRemappedOutput().toPath());

				throw new RuntimeException("Failed to remap: " + info.getRemappedNotation(), e);
			}
		}

		remapper.finish();

		for (ModDependencyInfo info : remapList) {
			outputConsumerMap.get(info).close();
			byte[] accessWidener = accessWidenerMap.get(info);

			if (accessWidener != null) {
				ZipUtils.replace(info.getRemappedOutput().toPath(), info.getAccessWidener(), accessWidener);
			}

			info.finaliseRemapping();
		}
	}

	public static JsonObject readInstallerJson(File file, Project project) {
		try {
			LoomGradleExtension extension = LoomGradleExtension.get(project);

			String jsonStr;

			try (JarFile jarFile = new JarFile(file)) {
				ZipEntry entry = jarFile.getEntry("fabric-installer.json");

				if (entry == null) {
					return null;
				}

				try (InputStream inputstream = jarFile.getInputStream(entry)) {
					jsonStr = new String(inputstream.readAllBytes(), StandardCharsets.UTF_8);
				}
			}

			return LoomGradlePlugin.GSON.fromJson(jsonStr, JsonObject.class);
		} catch (IOException e) {
			e.printStackTrace();
		}

		return null;
	}
}
