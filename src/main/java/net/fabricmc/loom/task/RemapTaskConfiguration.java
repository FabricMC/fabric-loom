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

import java.io.File;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.bundling.Jar;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.Constants;

public class RemapTaskConfiguration {
	public static final String REMAP_JAR_TASK_NAME = "remapJar";
	public static final String REMAP_SOURCES_JAR_TASK_NAME = "remapSourcesJar";

	public static void setupRemap(Project project) {
		final TaskContainer tasks = project.getTasks();
		final LoomGradleExtension extension = LoomGradleExtension.get(project);

		if (!extension.getRemapArchives().get()) {
			extension.getUnmappedModCollection().from(project.getTasks().getByName(JavaPlugin.JAR_TASK_NAME));
			return;
		}

		// Register the default remap jar task - must not be lazy to ensure that the prepare tasks get setup for other projects to depend on.
		RemapJarTask remapJarTask = tasks.create(REMAP_JAR_TASK_NAME, RemapJarTask.class, task -> {
			final AbstractArchiveTask jarTask = tasks.named(JavaPlugin.JAR_TASK_NAME, AbstractArchiveTask.class).get();

			// Basic task setup
			task.dependsOn(jarTask);
			task.setDescription("Remaps the built project jar to intermediary mappings.");
			task.setGroup(Constants.TaskGroup.FABRIC);
			project.getArtifacts().add(JavaPlugin.API_ELEMENTS_CONFIGURATION_NAME, task);
			project.getArtifacts().add(JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME, task);

			// Setup the input file and the nested deps
			task.getInputFile().convention(jarTask.getArchiveFile());
			task.dependsOn(tasks.named(JavaPlugin.JAR_TASK_NAME));
			task.getIncludesClientOnlyClasses().set(project.provider(extension::areEnvironmentSourceSetsSplit));
		});

		// Configure the default jar task
		tasks.named(JavaPlugin.JAR_TASK_NAME, AbstractArchiveTask.class).configure(task -> {
			task.getArchiveClassifier().convention("dev");
			task.getDestinationDirectory().set(new File(project.getBuildDir(), "devlibs"));
		});

		tasks.named(BasePlugin.ASSEMBLE_TASK_NAME).configure(task -> task.dependsOn(remapJarTask));

		trySetupSourceRemapping(project);

		if (!extension.getSetupRemappedVariants().get()) {
			return;
		}

		project.afterEvaluate(p -> {
			// Remove -dev jars from the default jar task
			for (String configurationName : new String[] { JavaPlugin.API_ELEMENTS_CONFIGURATION_NAME, JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME }) {
				Configuration configuration = project.getConfigurations().getByName(configurationName);
				final Task jarTask = project.getTasks().getByName(JavaPlugin.JAR_TASK_NAME);
				configuration.getArtifacts().removeIf(artifact -> {
					// if the artifact is a -dev jar and "builtBy jar"
					return "dev".equals(artifact.getClassifier()) && artifact.getBuildDependencies().getDependencies(null).contains(jarTask);
				});
			}
		});
	}

	private static void trySetupSourceRemapping(Project project) {
		final TaskContainer tasks = project.getTasks();
		final LoomGradleExtension extension = LoomGradleExtension.get(project);
		final JavaPluginExtension javaExtension = project.getExtensions().getByType(JavaPluginExtension.class);
		final String sourcesJarTaskName = javaExtension.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME).getSourcesJarTaskName();

		TaskProvider<RemapSourcesJarTask> remapSourcesTask = tasks.register(REMAP_SOURCES_JAR_TASK_NAME, RemapSourcesJarTask.class, task -> {
			task.setDescription("Remaps the default sources jar to intermediary mappings.");
			task.setGroup(Constants.TaskGroup.FABRIC);

			final Task sourcesTask = project.getTasks().findByName(sourcesJarTaskName);

			if (sourcesTask == null) {
				project.getLogger().info("{} task was not found, not remapping sources", sourcesJarTaskName);
				task.setEnabled(false);
				return;
			}

			if (!(sourcesTask instanceof Jar sourcesJarTask)) {
				project.getLogger().info("{} task is not a Jar task, not remapping sources", sourcesJarTaskName);
				task.setEnabled(false);
				return;
			}

			sourcesJarTask.getArchiveClassifier().convention("dev-sources");
			sourcesJarTask.getDestinationDirectory().set(new File(project.getBuildDir(), "devlibs"));
			task.getArchiveClassifier().convention("sources");

			task.dependsOn(sourcesJarTask);
			task.getInputFile().convention(sourcesJarTask.getArchiveFile());
		});

		tasks.named(BasePlugin.ASSEMBLE_TASK_NAME).configure(task -> task.dependsOn(remapSourcesTask));

		if (!extension.getSetupRemappedVariants().get()) {
			return;
		}

		project.afterEvaluate(p -> {
			final Task sourcesTask = project.getTasks().findByName(sourcesJarTaskName);

			if (!(sourcesTask instanceof Jar sourcesJarTask)) {
				return;
			}

			if (project.getConfigurations().getNames().contains(JavaPlugin.SOURCES_ELEMENTS_CONFIGURATION_NAME)) {
				project.getArtifacts().add(JavaPlugin.SOURCES_ELEMENTS_CONFIGURATION_NAME, remapSourcesTask);

				// Remove the dev sources artifact
				Configuration configuration = project.getConfigurations().getByName(JavaPlugin.SOURCES_ELEMENTS_CONFIGURATION_NAME);
				configuration.getArtifacts().removeIf(a -> a.getFile().equals(sourcesJarTask.getArchiveFile().get().getAsFile()));
			} else {
				// Sources jar may not have been created with withSourcesJar
				project.getLogger().warn("Not publishing sources jar as it was not found. Use java.withSourcesJar() to fix.");
			}
		});
	}
}
