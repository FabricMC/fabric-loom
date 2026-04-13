/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2026 FabricMC
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

package net.fabricmc.loom.configuration.ide;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.gradle.api.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.RunConfiguration;
import net.fabricmc.loom.configuration.InstallerData;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.gradle.GradleUtils;

public class RunConfigUtils {
	private static final Logger LOGGER = LoggerFactory.getLogger(RunConfigUtils.class);

	public static void createRunDirectory(RunConfiguration runConfiguration) throws IOException {
		Path runDirectory = runConfiguration.getRunDirectory().getAsFile().get().toPath();

		if (!Files.exists(runDirectory)) {
			Files.createDirectories(runDirectory);
		} else if (!Files.isDirectory(runDirectory)) {
			LOGGER.warn("Run directory {} is not a directory", runDirectory);
		}
	}

	@Nullable
	public static String getMainClass(String side, LoomGradleExtension extension) {
		InstallerData installerData = extension.getInstallerData();

		if (installerData == null) {
			return getDefaultMainClass(side);
		}

		JsonObject installerJson = installerData.installerJson();

		if (installerJson != null && installerJson.has("mainClass")) {
			JsonElement mainClassJson = installerJson.get("mainClass");

			String mainClassName = "";

			if (mainClassJson.isJsonObject()) {
				JsonObject mainClassesJson = mainClassJson.getAsJsonObject();

				if (mainClassesJson.has(side)) {
					mainClassName = mainClassesJson.get(side).getAsString();
				}
			} else {
				mainClassName = mainClassJson.getAsString();
			}

			return mainClassName;
		}

		return getDefaultMainClass(side);
	}

	@Nullable
	private static String getDefaultMainClass(String side) {
		return switch (side) {
		case "client" -> Constants.Knot.KNOT_CLIENT;
		case "server" -> Constants.Knot.KNOT_SERVER;
		default -> null;
		};
	}

	/**
	 * Format the run directory for use in an IDE run configuration. If the run directory is within the root project, it will be formatted as a relative path from the root project. Otherwise, it will be formatted as an absolute path.
	 *
	 * @param runConfig The run configuration to format the run directory for.
	 * @param project The project to format the run directory for.
	 * @param absoluteFormatter A function that formats an absolute path for use in an IDE run configuration.
	 * @param relativeFormatter A function that formats a relative path for use in an IDE run configuration.
	 */
	public static String formatRunDir(RunConfiguration runConfig, Project project, Function<File, String> absoluteFormatter, Function<String, String> relativeFormatter) {
		File runDir = runConfig.getRunDirectory().getAsFile().get();
		File projectDir = project.getRootProject().getProjectDir();

		if (runDir.toPath().startsWith(projectDir.toPath())) {
			String relativePath = projectDir.toPath().relativize(runDir.toPath()).toString();
			return relativeFormatter.apply(relativePath);
		} else {
			return absoluteFormatter.apply(runDir);
		}
	}

	// Copy the run configuration to a new instance that is safe to serialize, and finalise all values.
	// This does not inherit from the legacy RunConfigSettings class.
	static RunConfiguration toSerialisable(RunConfiguration runConfig, Project project) {
		RunConfigurationInternal runConfiguration = project.getObjects().newInstance(RunConfigurationInternal.class, runConfig.getName());
		runConfiguration.inherit(runConfig);
		runConfiguration.getIsFinalised().set(true);
		DefaultRunConfigurationSettings.finialiseValues(runConfiguration);
		return runConfiguration;
	}

	public static String getDisplayName(RunConfiguration run, Project project) {
		String displayName = run.getDisplayName().get();

		boolean appendProjectPath = run.getAppendProjectPathToDisplayName().get();

		if (appendProjectPath && !GradleUtils.isRootProject(project)) {
			displayName += " (" + project.getPath() + ")";
		}

		return displayName;
	}

	public static String formatEnvVars(RunConfiguration run, String pattern) {
		return run.getEnvironmentVars().get().entrySet().stream()
				.map(entry ->
						pattern.formatted(entry.getKey(), entry.getValue().toString())
				).collect(Collectors.joining());
	}
}
