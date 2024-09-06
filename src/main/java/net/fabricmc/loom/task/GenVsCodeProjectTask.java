/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2018-2024 FabricMC
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.services.ServiceReference;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.configuration.ide.RunConfig;
import net.fabricmc.loom.configuration.ide.RunConfigSettings;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.gradle.SyncTaskBuildService;

// Recommended vscode plugin pack:
// https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-pack
public abstract class GenVsCodeProjectTask extends AbstractLoomTask {
	// Prevent Gradle from running vscode task asynchronously
	@ServiceReference(SyncTaskBuildService.NAME)
	abstract Property<SyncTaskBuildService> getSyncTask();

	@Input
	protected abstract ListProperty<VsCodeConfiguration> getLaunchConfigurations();

	@OutputFile
	protected abstract RegularFileProperty getLaunchJson();

	@Inject
	public GenVsCodeProjectTask() {
		setGroup(Constants.TaskGroup.IDE);
		getLaunchConfigurations().set(getProject().provider(this::getConfigurations));
		getLaunchJson().convention(getProject().getRootProject().getLayout().getProjectDirectory().file("vscode/launch.json"));
	}

	private List<VsCodeConfiguration> getConfigurations() {
		List<VsCodeConfiguration> configurations = new ArrayList<>();

		for (RunConfigSettings settings : getExtension().getRunConfigs()) {
			if (!settings.isIdeConfigGenerated()) {
				continue;
			}

			final VsCodeConfiguration configuration = VsCodeConfiguration.fromRunConfig(getProject(), RunConfig.runConfig(getProject(), settings));
			configurations.add(configuration);
		}

		return configurations;
	}

	@TaskAction
	public void genRuns() throws IOException {
		final Path launchJson = getLaunchJson().get().getAsFile().toPath();

		if (Files.notExists(launchJson.getParent())) {
			Files.createDirectories(launchJson.getParent());
		}

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

		for (VsCodeConfiguration configuration : getLaunchConfigurations().get()) {
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

			Files.createDirectories(Paths.get(configuration.runDir));
		}

		final String json = LoomGradlePlugin.GSON.toJson(root);
		Files.writeString(launchJson, json, StandardCharsets.UTF_8);
	}

	public record VsCodeConfiguration(
			String type,
			String name,
			String request,
			String cwd,
			String console,
			boolean stopOnEntry,
			String mainClass,
			String vmArgs,
			String args,
			Map<String, Object> env,
			String projectName,
			String runDir) implements Serializable {
		public static VsCodeConfiguration fromRunConfig(Project project, RunConfig runConfig) {
			return new VsCodeConfiguration(
				"java",
				runConfig.configName,
				"launch",
				"${workspaceFolder}/" + runConfig.runDir,
				"integratedTerminal",
				false,
				runConfig.mainClass,
				RunConfig.joinArguments(runConfig.vmArgs),
				RunConfig.joinArguments(runConfig.programArgs),
				new HashMap<>(runConfig.environmentVariables),
				runConfig.projectName,
				project.getProjectDir().toPath().resolve(runConfig.runDir).toAbsolutePath().toString()
			);
		}
	}
}
