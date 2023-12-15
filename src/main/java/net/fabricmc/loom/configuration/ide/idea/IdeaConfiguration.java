/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021-2022 FabricMC
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;

import org.gradle.StartParameter;
import org.gradle.TaskExecutionRequest;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.internal.DefaultTaskExecutionRequest;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.ide.RunConfigSettings;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftJarConfiguration;
import net.fabricmc.loom.task.LoomTasks;

public abstract class IdeaConfiguration implements Runnable {
	private static final String INIT_SCRIPT_NAME = "ijmiscinit";
	private static final Pattern NOTATION_PATTERN = Pattern.compile("'net\\.minecraft:(?<name>.*):(.*):sources'");

	@Inject
	protected abstract Project getProject();

	public void run() {
		TaskProvider<IdeaSyncTask> ideaSyncTask = getProject().getTasks().register("ideaSyncTask", IdeaSyncTask.class, task -> {
			if (LoomGradleExtension.get(getProject()).getRunConfigs().stream().anyMatch(RunConfigSettings::isIdeConfigGenerated)) {
				task.dependsOn(LoomTasks.getIDELaunchConfigureTaskName(getProject()));
			} else {
				task.setEnabled(false);
			}
		});

		getProject().getTasks().configureEach(task -> {
			if (task.getName().equals("DownloadSources")) {
				hookDownloadSources(getProject(), task);
			}
		});

		if (!IdeaUtils.isIdeaSync()) {
			return;
		}

		final StartParameter startParameter = getProject().getGradle().getStartParameter();
		final List<TaskExecutionRequest> taskRequests = new ArrayList<>(startParameter.getTaskRequests());

		taskRequests.add(new DefaultTaskExecutionRequest(List.of("ideaSyncTask")));
		startParameter.setTaskRequests(taskRequests);
	}

	/*
		"Parse" the init script enough to figure out what jar we are talking about.

		Intelij code: https://github.com/JetBrains/intellij-community/blob/a09b1b84ab64a699794c860bc96774766dd38958/plugins/gradle/java/src/util/GradleAttachSourcesProvider.java
	 */
	private static void hookDownloadSources(Project project, Task task) {
		List<File> initScripts = project.getGradle().getStartParameter().getInitScripts();

		for (File initScript : initScripts) {
			if (!initScript.getName().contains(INIT_SCRIPT_NAME)) {
				continue;
			}

			try {
				final String script = Files.readString(initScript.toPath(), StandardCharsets.UTF_8);
				final String notation = parseInitScript(project, script);

				if (notation != null) {
					task.dependsOn(getGenSourcesTaskName(LoomGradleExtension.get(project), notation));
				}
			} catch (IOException e) {
				// Ignore
			}
		}
	}

	@Nullable
	private static String parseInitScript(Project project, String script) {
		if (!script.contains("Attempt to download sources from")
				|| !script.contains("downloadSources_")
				|| !script.contains("'%s'".formatted(project.getPath()))) {
			// Failed some basic sanity checks.
			return null;
		}

		// A little gross but should do the job nicely.
		final Matcher matcher = NOTATION_PATTERN.matcher(script);

		if (matcher.find()) {
			return matcher.group("name");
		}

		return null;
	}

	private static String getGenSourcesTaskName(LoomGradleExtension extension, String notation) {
		final MinecraftJarConfiguration configuration = extension.getMinecraftJarConfiguration().get();

		if (configuration == MinecraftJarConfiguration.SPLIT) {
			if (notation.toLowerCase(Locale.ROOT).contains("minecraft-clientonly")) {
				return "genClientOnlySources";
			}

			return "genCommonSources";
		}

		return "genSources";
	}
}
