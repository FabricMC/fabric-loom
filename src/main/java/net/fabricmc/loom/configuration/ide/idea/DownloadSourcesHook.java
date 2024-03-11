/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2023 FabricMC
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

package net.fabricmc.loom.configuration.ide.idea;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.mods.dependency.LocalMavenHelper;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftJar;
import net.fabricmc.loom.configuration.providers.minecraft.mapped.NamedMinecraftProvider;

// See: https://github.com/JetBrains/intellij-community/blob/a09b1b84ab64a699794c860bc96774766dd38958/plugins/gradle/java/src/util/GradleAttachSourcesProvider.java
record DownloadSourcesHook(Project project, Task task) {
	public static final String INIT_SCRIPT_NAME = "ijDownloadSources";
	private static final Pattern NOTATION_PATTERN = Pattern.compile("dependencyNotation = '(?<notation>.*)'");
	private static final Logger LOGGER = LoggerFactory.getLogger(DownloadSourcesHook.class);

	public static boolean hasInitScript(Project project) {
		List<File> initScripts = project.getGradle().getStartParameter().getInitScripts();

		for (File initScript : initScripts) {
			if (initScript.getName().contains(INIT_SCRIPT_NAME)) {
				return true;
			}
		}

		return false;
	}

	void tryHook() {
		List<File> initScripts = project.getGradle().getStartParameter().getInitScripts();

		for (File initScript : initScripts) {
			if (!initScript.getName().contains(INIT_SCRIPT_NAME)) {
				continue;
			}

			try {
				final String script = Files.readString(initScript.toPath(), StandardCharsets.UTF_8);
				final String notation = parseInitScript(script);

				if (notation == null) {
					LOGGER.debug("failed to parse init script dependency");
					continue;
				}

				final MinecraftJar.Type jarType = getJarType(notation);

				if (jarType == null) {
					LOGGER.debug("init script is trying to download sources for another Minecraft jar ({}) not used by this project ({})", notation, project.getPath());
					continue;
				}

				String sourcesTaskName = getGenSourcesTaskName(jarType);
				task.dependsOn(project.getTasks().named(sourcesTaskName));

				LOGGER.info("Running genSources task: {} in project: {} for {}", sourcesTaskName, project.getPath(), notation);
				break;
			} catch (IOException e) {
				// Ignore
			}
		}
	}

	@Nullable
	private String parseInitScript(String script) {
		if (!script.contains("IjDownloadTask")) {
			// Failed some basic sanity checks.
			return null;
		}

		// A little gross but should do the job nicely.
		final Matcher matcher = NOTATION_PATTERN.matcher(script);

		if (matcher.find()) {
			return matcher.group("notation");
		}

		return null;
	}

	private String getGenSourcesTaskName(MinecraftJar.Type jarType) {
		LoomGradleExtension extension = LoomGradleExtension.get(project);
		return extension.getMinecraftJarConfiguration().get()
				.createDecompileConfiguration(project)
				.getTaskName(jarType);
	}

	// Return the jar type, or null when this jar isnt used by the project
	@Nullable
	private MinecraftJar.Type getJarType(String name) {
		final LoomGradleExtension extension = LoomGradleExtension.get(project);
		final NamedMinecraftProvider<?> minecraftProvider = extension.getNamedMinecraftProvider();
		final List<MinecraftJar.Type> dependencyTypes = minecraftProvider.getDependencyTypes();

		if (dependencyTypes.isEmpty()) {
			throw new IllegalStateException();
		}

		for (MinecraftJar.Type type : dependencyTypes) {
			final LocalMavenHelper mavenHelper = minecraftProvider.getMavenHelper(type).withClassifier("sources");

			if (mavenHelper.getNotation().equals(name)) {
				return type;
			}
		}

		return null;
	}
}
