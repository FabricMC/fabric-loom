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

package net.fabricmc.loom.task;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.gradle.api.tasks.TaskAction;

import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.configuration.ide.RunConfig;
import net.fabricmc.loom.configuration.ide.RunConfigSettings;

// Recommended vscode plugin pack:
// https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-pack
public class GenVsCodeProjectTask extends AbstractLoomTask {
	@TaskAction
	public void genRuns() throws IOException {
		final Path projectDir = getProject().getRootDir().toPath().resolve(".vscode");

		if (Files.notExists(projectDir)) {
			Files.createDirectories(projectDir);
		}

		final Path launchJson = projectDir.resolve("launch.json");
		final JsonObject root;

		if (Files.exists(launchJson)) {
			root = LoomGradlePlugin.GSON.fromJson(Files.readString(launchJson, StandardCharsets.UTF_8), JsonObject.class);
		} else {
			root = new JsonObject();
			root.addProperty("version", "0.2.0");
		}

		final JsonArray configurations;

		if (root.has("configurations")) {
			configurations = root.getAsJsonArray("configurations");
		} else {
			configurations = new JsonArray();
			root.add("configurations", configurations);
		}

		for (RunConfigSettings settings : getExtension().getRunConfigs()) {
			if (!settings.isIdeConfigGenerated()) {
				continue;
			}

			final VsCodeConfiguration configuration = new VsCodeConfiguration(RunConfig.runConfig(getProject(), settings));
			final JsonElement configurationJson = LoomGradlePlugin.GSON.toJsonTree(configuration);

			final List<JsonElement> toRemove = new LinkedList<>();

			// Remove any existing with the same name
			for (JsonElement jsonElement : configurations) {
				if (!jsonElement.isJsonObject()) {
					continue;
				}

				final JsonObject jsonObject = jsonElement.getAsJsonObject();

				if (jsonObject.has("name")) {
					if (jsonObject.get("name").getAsString().equalsIgnoreCase(configuration.name)) {
						toRemove.add(jsonElement);
					}
				}
			}

			toRemove.forEach(configurations::remove);

			configurations.add(configurationJson);
			settings.makeRunDir();
		}

		final String json = LoomGradlePlugin.GSON.toJson(root);
		Files.writeString(launchJson, json, StandardCharsets.UTF_8);
	}

	private class VsCodeLaunch {
		public String version = "0.2.0";
		public List<VsCodeConfiguration> configurations = new ArrayList<>();

		public void add(RunConfig runConfig) {
			configurations.add(new VsCodeConfiguration(runConfig));
		}
	}

	@SuppressWarnings("unused")
	private class VsCodeConfiguration {
		public String type = "java";
		public String name;
		public String request = "launch";
		public String cwd;
		public String console = "internalConsole";
		public boolean stopOnEntry = false;
		public String mainClass;
		public String vmArgs;
		public String args;
		public Map<String, Object> env;
		public String projectName;

		VsCodeConfiguration(RunConfig runConfig) {
			this.name = runConfig.configName;
			this.mainClass = runConfig.mainClass;
			this.vmArgs = RunConfig.joinArguments(runConfig.vmArgs);
			this.args = RunConfig.joinArguments(runConfig.programArgs);
			this.cwd = "${workspaceFolder}/" + runConfig.runDir;
			this.env = new HashMap<>(runConfig.environmentVariables);
			this.projectName = runConfig.projectName;

			if (getProject().getRootProject() != getProject()) {
				Path rootPath = getProject().getRootDir().toPath();
				Path projectPath = getProject().getProjectDir().toPath();
				String relativePath = rootPath.relativize(projectPath).toString();

				this.cwd = "${workspaceFolder}/%s/%s".formatted(relativePath, runConfig.runDir);
			}
		}
	}
}
