/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2021 FabricMC
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

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.dsl.ArtifactHandler;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.jvm.tasks.Jar;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.build.nesting.NestableJarGenerationTask;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.gradle.GradleUtils;
import net.fabricmc.loom.util.gradle.SourceSetHelper;
import net.fabricmc.loom.util.gradle.SyncTaskBuildService;

public abstract class RemapTaskConfiguration implements Runnable {
	public static final String REMAP_JAR_TASK_NAME = "remapJar";
	public static final String REMAP_SOURCES_JAR_TASK_NAME = "remapSourcesJar";

	@Inject
	protected abstract Project getProject();

	@Inject
	protected abstract TaskContainer getTasks();

	@Inject
	protected abstract ArtifactHandler getArtifacts();

	@Inject
	protected abstract ConfigurationContainer getConfigurations();

	public void run() {
		final LoomGradleExtension extension = LoomGradleExtension.get(getProject());

		SyncTaskBuildService.register(getProject());

		if (GradleUtils.getBooleanProperty(getProject(), Constants.Properties.DONT_REMAP)) {
			extension.getUnmappedModCollection().from(getTasks().getByName(JavaPlugin.JAR_TASK_NAME));
			return;
		}

		Configuration includeConfiguration = getProject().getConfigurations().getByName(Constants.Configurations.INCLUDE_INTERNAL);
		getTasks().register(Constants.Task.PROCESS_INCLUDE_JARS, NestableJarGenerationTask.class, task -> {
			task.from(includeConfiguration);
			task.getOutputDirectory().set(getProject().getLayout().getBuildDirectory().dir(task.getName()));
		});

		Action<RemapJarTask> remapJarTaskAction = task -> {
			final TaskProvider<AbstractArchiveTask> jarTask = getTasks().named(JavaPlugin.JAR_TASK_NAME, AbstractArchiveTask.class);

			// Basic task setup
			task.dependsOn(jarTask);
			task.setDescription("Remaps the built project jar to intermediary mappings.");
			task.setGroup(Constants.TaskGroup.FABRIC);
			getArtifacts().add(JavaPlugin.API_ELEMENTS_CONFIGURATION_NAME, task);
			getArtifacts().add(JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME, task);

			// Setup the input file and the nested deps
			task.getInputFile().convention(jarTask.flatMap(AbstractArchiveTask::getArchiveFile));
			task.dependsOn(getTasks().named(JavaPlugin.JAR_TASK_NAME));
			task.getIncludesClientOnlyClasses().set(getProject().provider(extension::areEnvironmentSourceSetsSplit));
		};

		// must not be lazy to ensure that the prepare tasks get setup for other projects to depend on.
		// Being lazy also breaks maven publishing, see: https://github.com/FabricMC/fabric-loom/issues/1023
		getTasks().create(REMAP_JAR_TASK_NAME, RemapJarTask.class, remapJarTaskAction);

		// Configure the default jar task
		getTasks().named(JavaPlugin.JAR_TASK_NAME, AbstractArchiveTask.class).configure(task -> {
			task.getArchiveClassifier().convention("dev");
			task.getDestinationDirectory().set(getProject().getLayout().getBuildDirectory().map(directory -> directory.dir("devlibs")));
		});

		getTasks().named(BasePlugin.ASSEMBLE_TASK_NAME).configure(task -> task.dependsOn(getTasks().named(REMAP_JAR_TASK_NAME)));

		trySetupSourceRemapping();

		if (GradleUtils.getBooleanProperty(getProject(), Constants.Properties.DISABLE_REMAPPED_VARIANTS)) {
			return;
		}

		GradleUtils.afterSuccessfulEvaluation(getProject(), () -> {
			// Remove -dev jars from the default jar task
			for (String configurationName : new String[] { JavaPlugin.API_ELEMENTS_CONFIGURATION_NAME, JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME }) {
				Configuration configuration = getConfigurations().getByName(configurationName);
				final Jar jarTask = (Jar) getTasks().getByName(JavaPlugin.JAR_TASK_NAME);
				configuration.getArtifacts().removeIf(artifact -> {
					// if the artifact is built by the jar task, and has the same output path.
					return artifact.getFile().getAbsolutePath().equals(jarTask.getArchiveFile().get().getAsFile().getAbsolutePath())
							&& (extension.isProjectIsolationActive() || artifact.getBuildDependencies().getDependencies(null).contains(jarTask));
				});
			}
		});
	}

	private void trySetupSourceRemapping() {
		final LoomGradleExtension extension = LoomGradleExtension.get(getProject());

		TaskProvider<RemapSourcesJarTask> remapSourcesTask = getTasks().register(REMAP_SOURCES_JAR_TASK_NAME, RemapSourcesJarTask.class, task -> {
			task.setDescription("Remaps the default sources jar to intermediary mappings.");
			task.setGroup(Constants.TaskGroup.FABRIC);
			task.getIncludesClientOnlyClasses().set(getProject().provider(extension::areEnvironmentSourceSetsSplit));
		});

		getTasks().named(BasePlugin.ASSEMBLE_TASK_NAME).configure(task -> task.dependsOn(remapSourcesTask));

		GradleUtils.afterSuccessfulEvaluation(getProject(), () -> {
			final String sourcesJarTaskName = SourceSetHelper.getMainSourceSet(getProject()).getSourcesJarTaskName();
			final Task sourcesTask = getTasks().findByName(sourcesJarTaskName);

			boolean canRemap = true;

			if (sourcesTask == null) {
				getProject().getLogger().info("{} task was not found, not remapping sources", sourcesJarTaskName);
				canRemap = false;
			}

			if (canRemap && !(sourcesTask instanceof Jar)) {
				getProject().getLogger().info("{} task is not a Jar task, not remapping sources", sourcesJarTaskName);
				canRemap = false;
			}

			boolean finalCanRemap = canRemap;

			remapSourcesTask.configure(task -> {
				if (!finalCanRemap) {
					task.setEnabled(false);
					return;
				}

				final Jar sourcesJarTask = (Jar) sourcesTask;

				sourcesJarTask.getArchiveClassifier().convention("dev-sources");
				sourcesJarTask.getDestinationDirectory().set(getProject().getLayout().getBuildDirectory().map(directory -> directory.dir("devlibs")));
				task.getArchiveClassifier().convention("sources");

				task.dependsOn(sourcesJarTask);
				task.getInputFile().convention(sourcesJarTask.getArchiveFile());
			});

			if (GradleUtils.getBooleanProperty(getProject(), "fabric.loom.disableRemappedVariants")) {
				return;
			}

			if (getConfigurations().getNames().contains(JavaPlugin.SOURCES_ELEMENTS_CONFIGURATION_NAME)) {
				// Remove the dev sources artifact
				Configuration configuration = getConfigurations().getByName(JavaPlugin.SOURCES_ELEMENTS_CONFIGURATION_NAME);
				configuration.getArtifacts().removeIf(a -> "sources".equals(a.getClassifier()));

				// Add the remapped sources artifact
				getArtifacts().add(JavaPlugin.SOURCES_ELEMENTS_CONFIGURATION_NAME, remapSourcesTask.map(AbstractArchiveTask::getArchiveFile), artifact -> {
					artifact.setClassifier("sources");
				});
			} else if (canRemap) {
				// Sources jar may not have been created with withSourcesJar
				getProject().getLogger().warn("Not publishing sources jar as it was not created by the java plugin. Use java.withSourcesJar() to fix.");
			}
		});
	}
}
