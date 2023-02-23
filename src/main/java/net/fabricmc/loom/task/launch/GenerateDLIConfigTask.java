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

package net.fabricmc.loom.task.launch;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.gradle.api.Project;
import org.gradle.api.logging.configuration.ConsoleOutput;
import org.gradle.api.tasks.TaskAction;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftVersionMeta;
import net.fabricmc.loom.configuration.providers.minecraft.mapped.MappedMinecraftProvider;
import net.fabricmc.loom.task.AbstractLoomTask;
import net.fabricmc.loom.util.gradle.SourceSetHelper;

public abstract class GenerateDLIConfigTask extends AbstractLoomTask {
	@TaskAction
	public void run() throws IOException {
		final MinecraftVersionMeta versionInfo = getExtension().getMinecraftProvider().getVersionInfo();
		File assetsDirectory = new File(getExtension().getFiles().getUserCache(), "assets");

		if (versionInfo.assets().equals("legacy")) {
			assetsDirectory = new File(assetsDirectory, "/legacy/" + versionInfo.id());
		}

		final LaunchConfig launchConfig = new LaunchConfig()
				.property("fabric.development", "true")
				.property("fabric.remapClasspathFile", getExtension().getFiles().getRemapClasspathFile().getAbsolutePath())
				.property("log4j.configurationFile", getAllLog4JConfigFiles(getExtension()))
				.property("log4j2.formatMsgNoLookups", "true")

				.argument("client", "--assetIndex")
				.argument("client", getExtension().getMinecraftProvider().getVersionInfo().assetIndex().fabricId(getExtension().getMinecraftProvider().minecraftVersion()))
				.argument("client", "--assetsDir")
				.argument("client", assetsDirectory.getAbsolutePath());

		if (versionInfo.hasNativesToExtract()) {
			String nativesPath = getExtension().getFiles().getNativesDirectory(getProject()).getAbsolutePath();

			launchConfig
					.property("client", "java.library.path", nativesPath)
					.property("client", "org.lwjgl.librarypath", nativesPath);
		}

		if (getExtension().areEnvironmentSourceSetsSplit()) {
			launchConfig.property("client", "fabric.gameJarPath.client", getGameJarPath(getExtension(), "client"));
			launchConfig.property("fabric.gameJarPath", getGameJarPath(getExtension(), "common"));
		}

		if (!getExtension().getMods().isEmpty()) {
			launchConfig.property("fabric.classPathGroups", getClassPathGroups(getExtension(), getProject()));
		}

		final boolean plainConsole = getProject().getGradle().getStartParameter().getConsoleOutput() == ConsoleOutput.Plain;
		final boolean ansiSupportedIDE = new File(getProject().getRootDir(), ".vscode").exists()
				|| new File(getProject().getRootDir(), ".idea").exists()
				|| (Arrays.stream(getProject().getRootDir().listFiles()).anyMatch(file -> file.getName().endsWith(".iws")));

		//Enable ansi by default for idea and vscode when gradle is not ran with plain console.
		if (ansiSupportedIDE && !plainConsole) {
			launchConfig.property("fabric.log.disableAnsi", "false");
		}

		FileUtils.writeStringToFile(getExtension().getFiles().getDevLauncherConfig(), launchConfig.asString(), StandardCharsets.UTF_8);
	}

	public static String getAllLog4JConfigFiles(LoomGradleExtension extension) {
		return extension.getLog4jConfigs().getFiles().stream()
				.map(File::getAbsolutePath)
				.collect(Collectors.joining(","));
	}

	public static String getGameJarPath(LoomGradleExtension extension, String env) {
		MappedMinecraftProvider.Split split = (MappedMinecraftProvider.Split) extension.getNamedMinecraftProvider();

		return switch (env) {
		case "client" -> split.getClientOnlyJar().getPath().toAbsolutePath().toString();
		case "common" -> split.getCommonJar().getPath().toAbsolutePath().toString();
		default -> throw new UnsupportedOperationException();
		};
	}

	/**
	 * See: https://github.com/FabricMC/fabric-loader/pull/585.
	 */
	public static String getClassPathGroups(LoomGradleExtension extension, Project project) {
		return extension.getMods().stream()
				.map(modSettings ->
						SourceSetHelper.getClasspath(modSettings, project).stream()
							.map(File::getAbsolutePath)
							.collect(Collectors.joining(File.pathSeparator))
				)
				.collect(Collectors.joining(File.pathSeparator+File.pathSeparator));
	}

	public static class LaunchConfig {
		private final Map<String, List<String>> values = new HashMap<>();

		public LaunchConfig property(String key, String value) {
			return property("common", key, value);
		}

		public LaunchConfig property(String side, String key, String value) {
			values.computeIfAbsent(side + "Properties", (s -> new ArrayList<>()))
					.add(String.format("%s=%s", key, value));
			return this;
		}

		public LaunchConfig argument(String value) {
			return argument("common", value);
		}

		public LaunchConfig argument(String side, String value) {
			values.computeIfAbsent(side + "Args", (s -> new ArrayList<>()))
					.add(value);
			return this;
		}

		public String asString() {
			StringJoiner stringJoiner = new StringJoiner("\n");

			for (Map.Entry<String, List<String>> entry : values.entrySet()) {
				stringJoiner.add(entry.getKey());

				for (String s : entry.getValue()) {
					stringJoiner.add("\t" + s);
				}
			}

			return stringJoiner.toString();
		}
	}
}
