/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016, 2017, 2018 FabricMC
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

package net.fabricmc.loom.util;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.apache.commons.io.IOUtils;
import org.gradle.api.Project;
import org.objectweb.asm.commons.Remapper;
import org.zeroturnaround.zip.ZipUtil;
import org.zeroturnaround.zip.transform.StringZipEntryTransformer;
import org.zeroturnaround.zip.transform.ZipEntryTransformerEntry;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.providers.MappingsProvider;
import net.fabricmc.loom.providers.MinecraftMappedProvider;
import net.fabricmc.loom.util.accesswidener.AccessWidener;
import net.fabricmc.loom.util.accesswidener.AccessWidenerRemapper;
import net.fabricmc.loom.processors.dependency.ModDependencyInfo;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.InputTag;
import net.fabricmc.tinyremapper.OutputConsumerPath;

public class ModProcessor {
	public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static void processMods(Project project, List<ModDependencyInfo> processList) throws IOException {
		if (processList.stream().noneMatch(ModDependencyInfo::requiresRemapping)) {
			return;
		}

		for (ModDependencyInfo info : processList) {
			if (info.requiresRemapping() && info.getRemappedOutput().exists()) {
				info.getRemappedOutput().delete();
			}
		}

		remapJars(project, processList);

		for (ModDependencyInfo info : processList) {
			if (!info.getRemappedOutput().exists()) {
				throw new RuntimeException("Failed to remap mod" + info);
			}

			if (info.requiresRemapping()) {
				stripNestedJars(info.getRemappedOutput());
			}
		}
	}

	private static void stripNestedJars(File file) {
		//Strip out all contained jar info as we dont want loader to try and load the jars contained in dev.
		ZipUtil.transformEntries(file, new ZipEntryTransformerEntry[] {(new ZipEntryTransformerEntry("fabric.mod.json", new StringZipEntryTransformer() {
			@Override
			protected String transform(ZipEntry zipEntry, String input) throws IOException {
				JsonObject json = GSON.fromJson(input, JsonObject.class);
				json.remove("jars");
				return GSON.toJson(json);
			}
		}))});
	}

	private static byte[] remapAccessWidener(byte[] input, Remapper remapper) {
		try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(input), StandardCharsets.UTF_8))) {
			AccessWidener accessWidener = new AccessWidener();
			accessWidener.read(bufferedReader);

			AccessWidenerRemapper accessWidenerRemapper = new AccessWidenerRemapper(accessWidener, remapper, "named");
			AccessWidener remapped = accessWidenerRemapper.remap();

			try (StringWriter writer = new StringWriter()) {
				remapped.write(writer);
				return writer.toString().getBytes(StandardCharsets.UTF_8);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static void remapJars(Project project, List<ModDependencyInfo> processList) throws IOException {
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		String fromM = "intermediary";
		String toM = "named";

		MinecraftMappedProvider mappedProvider = extension.getMinecraftMappedProvider();
		MappingsProvider mappingsProvider = extension.getMappingsProvider();

		Path mc = mappedProvider.getIntermediaryJar().toPath();
		Path[] mcDeps = mappedProvider.getMapperPaths().stream().map(File::toPath).toArray(Path[]::new);

		List<ModDependencyInfo> remapList = processList.stream().filter(ModDependencyInfo::requiresRemapping).collect(Collectors.toList());

		project.getLogger().lifecycle(":remapping " + remapList.size() + " mods (TinyRemapper, " + fromM + " -> " + toM + ")");

		TinyRemapper remapper = TinyRemapper.newRemapper()
						.withMappings(TinyRemapperMappingsHelper.create(mappingsProvider.getMappings(), fromM, toM, false))
						.renameInvalidLocals(false)
						.build();

		remapper.readClassPathAsync(mc);
		remapper.readClassPathAsync(mcDeps);

		final Map<ModDependencyInfo, InputTag> tagMap = new HashMap<>();
		final Map<ModDependencyInfo, OutputConsumerPath> outputConsumerMap = new HashMap<>();
		final Map<ModDependencyInfo, byte[]> accessWidenerMap = new HashMap<>();

		for (RemappedConfigurationEntry entry : Constants.MOD_COMPILE_ENTRIES) {
			for (File inputFile : project.getConfigurations().getByName(entry.getSourceConfiguration()).getFiles()) {
				if (remapList.stream().noneMatch(info -> info.getInputFile().equals(inputFile))) {
					project.getLogger().info("Adding " + inputFile + " onto the remap classpath");
					remapper.readClassPathAsync(inputFile.toPath());
				}
			}
		}

		for (ModDependencyInfo info : remapList) {
			InputTag tag = remapper.createInputTag();
			project.getLogger().info("Adding " + info.getInputFile() + " as a remap input");
			remapper.readInputsAsync(tag, info.getInputFile().toPath());
			tagMap.put(info, tag);
		}

		// Apply this in a second loop as we need to ensure all the inputs are on the classpath before remapping.
		for (ModDependencyInfo info : remapList) {
			OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(info.getRemappedOutput().toPath()).build();
			outputConsumer.addNonClassFiles(info.getInputFile().toPath());
			outputConsumerMap.put(info, outputConsumer);
			String accessWidener = info.getAccessWidener();

			if (accessWidener != null) {
				accessWidenerMap.put(info, remapAccessWidener(ZipUtil.unpackEntry(info.inputFile, accessWidener), remapper.getRemapper()));
			}

			remapper.apply(outputConsumer, tagMap.get(info));
		}

		remapper.finish();

		for (ModDependencyInfo info : remapList) {
			outputConsumerMap.get(info).close();
			byte[] accessWidener = accessWidenerMap.get(info);

			if (accessWidener != null) {
				ZipUtil.replaceEntry(info.getRemappedOutput(), info.getAccessWidener(), accessWidener);
			}
		}
	}

	static JsonObject readInstallerJson(File file, Project project) {
		try {
			LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
			String launchMethod = extension.getLoaderLaunchMethod();

			String jsonStr;
			int priority = 0;

			try (JarFile jarFile = new JarFile(file)) {
				ZipEntry entry = null;

				if (!launchMethod.isEmpty()) {
					entry = jarFile.getEntry("fabric-installer." + launchMethod + ".json");

					if (entry == null) {
						project.getLogger().warn("Could not find loader launch method '" + launchMethod + "', falling back");
					}
				}

				if (entry == null) {
					entry = jarFile.getEntry("fabric-installer.json");
					priority++;

					if (entry == null) {
						return null;
					}
				}

				try (InputStream inputstream = jarFile.getInputStream(entry)) {
					jsonStr = IOUtils.toString(inputstream, StandardCharsets.UTF_8);
				}
			}

			JsonObject jsonObject = GSON.fromJson(jsonStr, JsonObject.class);
			return jsonObject;
		} catch (IOException e) {
			e.printStackTrace();
		}

		return null;
	}
}
