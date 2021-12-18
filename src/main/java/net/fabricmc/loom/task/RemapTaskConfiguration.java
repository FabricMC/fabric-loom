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
import org.gradle.api.artifacts.PublishArtifact;
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
	private static final String REMAP_JAR_TASK_NAME = "remapJar";
	private static final String REMAP_SOURCES_JAR_TASK_NAME = "remapSourcesJar";

	// TODO respect extension.getSetupRemappedVariants in here
	public static void setupRemap(Project project) {
		final TaskContainer tasks = project.getTasks();
		final LoomGradleExtension extension = LoomGradleExtension.get(project);

		if (!extension.getRemapArchives().get()) {
			extension.getUnmappedModCollection().from(project.getTasks().getByName(JavaPlugin.JAR_TASK_NAME));
			return;
		}

		// Register the default remap jar task
		TaskProvider<RemapJarTask> remapJarTaskProvider = tasks.register(REMAP_JAR_TASK_NAME, RemapJarTask.class, task -> {
			final AbstractArchiveTask jarTask = tasks.named(JavaPlugin.JAR_TASK_NAME, AbstractArchiveTask.class).get();

			// Basic task setup
			task.dependsOn(jarTask);
			task.setDescription("Remaps the built project jar to intermediary mappings.");
			task.setGroup(Constants.TaskGroup.FABRIC);
			project.getArtifacts().add(JavaPlugin.API_ELEMENTS_CONFIGURATION_NAME, task);
			project.getArtifacts().add(JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME, task);

			// Setup the input file and the nested deps
			task.getInputFile().convention(jarTask.getArchiveFile());
			task.getNestedJars().from(project.getConfigurations().getByName(Constants.Configurations.INCLUDE));
		});

		// Configure the default jar task
		tasks.named(JavaPlugin.JAR_TASK_NAME, AbstractArchiveTask.class).configure(task -> {
			task.getArchiveClassifier().convention("dev");
			task.finalizedBy(remapJarTaskProvider);
			task.getDestinationDirectory().set(new File(project.getBuildDir(), "devlibs"));
		});

		trySetupSourceRemapping(project);

		// Remove -dev jars from the default jar task
		for (String configurationName : new String[] { JavaPlugin.API_ELEMENTS_CONFIGURATION_NAME, JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME }) {
			Configuration configuration = project.getConfigurations().getByName(configurationName);
			configuration.getArtifacts().removeIf(artifact -> {
				Task jarTask = project.getTasks().getByName(JavaPlugin.JAR_TASK_NAME);
				// if the artifact is a -dev jar and "builtBy jar"
				return "dev".equals(artifact.getClassifier()) && artifact.getBuildDependencies().getDependencies(null).contains(jarTask);
			});
		}
	}

	private static void trySetupSourceRemapping(Project project) {
		final TaskContainer tasks = project.getTasks();

		tasks.register(REMAP_SOURCES_JAR_TASK_NAME, RemapSourcesJarTask.class, task -> {
			task.setDescription("Remaps the default sources jar to intermediary mappings.");
			task.setGroup(Constants.TaskGroup.FABRIC);

			final JavaPluginExtension javaExtension = project.getExtensions().getByType(JavaPluginExtension.class);
			final String sourcesJarTaskName = javaExtension.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME).getSourcesJarTaskName();
			final Task sourcesTask = project.getTasks().findByName(sourcesJarTaskName);

			if (sourcesTask == null) {
				project.getLogger().info("{} task was not found, not remapping sources", sourcesJarTaskName);
				return;
			}

			if (!(sourcesTask instanceof Jar sourcesJarTask)) {
				project.getLogger().info("{} task is not a Jar task, not remapping sources", sourcesJarTaskName);
				return;
			}

			sourcesJarTask.getDestinationDirectory().set(new File(project.getBuildDir(), "devlibs"));
			task.getArchiveClassifier().convention("sources");

			task.dependsOn(sourcesJarTask);
			task.getInputFile().convention(sourcesJarTask.getArchiveFile());

			PublishArtifact artifact = project.getArtifacts().add(JavaPlugin.SOURCES_ELEMENTS_CONFIGURATION_NAME, task);

			// Remove the existing artifact that does not run remapSourcesJar.
			// It doesn't seem to hurt, but I'm not sure if the file-level duplicates cause issues.
			Configuration configuration = project.getConfigurations().getByName(JavaPlugin.SOURCES_ELEMENTS_CONFIGURATION_NAME);
			configuration.getArtifacts().removeIf(a -> a != artifact && artifact.getFile().equals(a.getFile()));
		});
	}
}
