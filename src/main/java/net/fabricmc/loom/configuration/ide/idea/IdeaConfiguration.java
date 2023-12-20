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

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.gradle.StartParameter;
import org.gradle.TaskExecutionRequest;
import org.gradle.api.Project;
import org.gradle.internal.DefaultTaskExecutionRequest;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.ide.RunConfigSettings;
import net.fabricmc.loom.task.LoomTasks;
import net.fabricmc.loom.util.gradle.GradleUtils;

public abstract class IdeaConfiguration implements Runnable {
	@Inject
	protected abstract Project getProject();

	public void run() {
		getProject().getTasks().register("ideaSyncTask", IdeaSyncTask.class, task -> {
			if (LoomGradleExtension.get(getProject()).getRunConfigs().stream().anyMatch(RunConfigSettings::isIdeConfigGenerated)) {
				task.dependsOn(LoomTasks.getIDELaunchConfigureTaskName(getProject()));
			} else {
				task.setEnabled(false);
			}
		});

		hookDownloadSources();

		if (!IdeaUtils.isIdeaSync()) {
			return;
		}

		final StartParameter startParameter = getProject().getGradle().getStartParameter();
		final List<TaskExecutionRequest> taskRequests = new ArrayList<>(startParameter.getTaskRequests());

		taskRequests.add(new DefaultTaskExecutionRequest(List.of("ideaSyncTask")));
		startParameter.setTaskRequests(taskRequests);
	}

	private void hookDownloadSources() {
		LoomGradleExtension extension = LoomGradleExtension.get(getProject());

		if (!extension.isRootProject()) {
			return;
		}

		if (!DownloadSourcesHook.hasInitScript(getProject())) {
			return;
		}

		getProject().getTasks().configureEach(task -> {
			if (task.getName().startsWith(DownloadSourcesHook.INIT_SCRIPT_NAME)) {
				getProject().allprojects(subProject -> {
					if (!GradleUtils.isLoomProject(subProject)) {
						return;
					}

					new DownloadSourcesHook(subProject, task).tryHook();
				});
			}
		});
	}
}
