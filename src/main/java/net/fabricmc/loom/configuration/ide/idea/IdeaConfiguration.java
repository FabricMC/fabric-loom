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

package net.fabricmc.loom.configuration.ide.idea;

import java.util.Objects;

import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.gradle.plugins.ide.idea.model.IdeaProject;
import org.jetbrains.gradle.ext.ActionDelegationConfig;
import org.jetbrains.gradle.ext.IdeaExtPlugin;
import org.jetbrains.gradle.ext.ProjectSettings;
import org.jetbrains.gradle.ext.RunConfiguration;
import org.jetbrains.gradle.ext.TaskTriggersConfig;

public class IdeaConfiguration {
	public static void setup(Project project) {
		TaskProvider<IdeaSyncTask> ideaSyncTask = project.getTasks().register("ideaSyncTask", IdeaSyncTask.class, ideaSyncTask1 -> {
			ideaSyncTask1.dependsOn(project.getTasks().named("downloadAssets"));
		});

		if (!IdeaUtils.isIdeaSync()) {
			return;
		}

		project.getPlugins().apply(IdeaExtPlugin.class);
		project.getPlugins().withType(IdeaExtPlugin.class, ideaExtPlugin -> {
			if (project != project.getRootProject()) {
				// Also ensure it's applied to the root project.
				project.getRootProject().getPlugins().apply(IdeaExtPlugin.class);
			}

			final IdeaModel ideaModel = project.getRootProject().getExtensions().findByType(IdeaModel.class);

			if (ideaModel == null) {
				return;
			}

			final IdeaProject ideaProject = ideaModel.getProject();

			if (ideaProject == null) {
				return;
			}

			final ProjectSettings settings = getExtension(ideaProject, ProjectSettings.class);
			final ActionDelegationConfig delegateActions = getExtension(settings, ActionDelegationConfig.class);
			final TaskTriggersConfig taskTriggers = getExtension(settings, TaskTriggersConfig.class);
			final NamedDomainObjectContainer<RunConfiguration> runConfigurations = (NamedDomainObjectContainer<RunConfiguration>) ((ExtensionAware) settings).getExtensions().getByName("runConfigurations");

			// Run the sync task on import
			taskTriggers.afterSync(ideaSyncTask);
		});
	}

	private static <T> T getExtension(Object extensionAware, Class<T> type) {
		return Objects.requireNonNull(((ExtensionAware) extensionAware).getExtensions().getByType(type));
	}
}
