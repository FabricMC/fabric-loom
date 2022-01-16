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

import java.util.List;

import com.google.common.base.Preconditions;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.ide.RunConfigSettings;
import net.fabricmc.loom.task.launch.GenerateDLIConfigTask;
import net.fabricmc.loom.task.launch.GenerateLog4jConfigTask;
import net.fabricmc.loom.task.launch.GenerateRemapClasspathTask;
import net.fabricmc.loom.util.Constants;

public final class LoomTasks {
	private LoomTasks() {
	}

	public static void registerTasks(Project project) {
		TaskContainer tasks = project.getTasks();

		tasks.register("migrateMappings", MigrateMappingsTask.class, t -> {
			t.setDescription("Migrates mappings to a new version.");
			t.getOutputs().upToDateWhen(o -> false);
		});

		RemapTaskConfiguration.setupRemap(project);

		tasks.register("extractNatives", ExtractNativesTask.class, t -> {
			t.setDescription("Extracts the minecraft platform specific natives.");
		});
		tasks.register("downloadAssets", DownloadAssetsTask.class, t -> {
			t.setDescription("Downloads required assets for Fabric.");
		});
		tasks.register("generateDLIConfig", GenerateDLIConfigTask.class, t -> {
			t.setDescription("Generate the DevLaunchInjector config file");
		});
		tasks.register("generateLog4jConfig", GenerateLog4jConfigTask.class, t -> {
			t.setDescription("Generate the log4j config file");
		});
		tasks.register("generateRemapClasspath", GenerateRemapClasspathTask.class, t -> {
			t.setDescription("Generate the remap classpath file");
		});

		tasks.register("configureLaunch", task -> {
			task.dependsOn(tasks.named("generateDLIConfig"));
			task.dependsOn(tasks.named("generateLog4jConfig"));
			task.dependsOn(tasks.named("generateRemapClasspath"));

			task.setDescription("Setup the required files to launch Minecraft");
			task.setGroup(Constants.TaskGroup.FABRIC);
		});

		tasks.register("configureClientLaunch", task -> {
			task.dependsOn(tasks.named("extractNatives"));
			task.dependsOn(tasks.named("downloadAssets"));
			task.dependsOn(tasks.named("configureLaunch"));

			task.setDescription("Setup the required files to launch the Minecraft client");
			task.setGroup(Constants.TaskGroup.FABRIC);
		});

		TaskProvider<ValidateAccessWidenerTask> validateAccessWidener = tasks.register("validateAccessWidener", ValidateAccessWidenerTask.class, t -> {
			t.setDescription("Validate all the rules in the access widener against the Minecraft jar");
			t.setGroup("verification");
		});

		tasks.named("check").configure(task -> task.dependsOn(validateAccessWidener));

		registerIDETasks(tasks);
		registerRunTasks(tasks, project);
	}

	private static void registerIDETasks(TaskContainer tasks) {
		tasks.register("genIdeaWorkspace", GenIdeaProjectTask.class, t -> {
			t.setDescription("Generates an IntelliJ IDEA workspace from this project.");
			t.dependsOn("idea", "configureLaunch");
			t.setGroup(Constants.TaskGroup.IDE);
		});

		tasks.register("genEclipseRuns", GenEclipseRunsTask.class, t -> {
			t.setDescription("Generates Eclipse run configurations for this project.");
			t.dependsOn("configureLaunch");
			t.setGroup(Constants.TaskGroup.IDE);
		});

		tasks.register("cleanEclipseRuns", CleanEclipseRunsTask.class, t -> {
			t.setDescription("Removes Eclipse run configurations for this project.");
			t.setGroup(Constants.TaskGroup.IDE);
		});

		tasks.register("vscode", GenVsCodeProjectTask.class, t -> {
			t.setDescription("Generates VSCode launch configurations.");
			t.dependsOn("configureLaunch");
			t.setGroup(Constants.TaskGroup.IDE);
		});
	}

	private static void registerRunTasks(TaskContainer tasks, Project project) {
		LoomGradleExtension extension = LoomGradleExtension.get(project);

		Preconditions.checkArgument(extension.getRunConfigs().size() == 0, "Run configurations must not be registered before loom");

		extension.getRunConfigs().whenObjectAdded(config -> {
			String configName = config.getName();
			String taskName = "run" + configName.substring(0, 1).toUpperCase() + configName.substring(1);

			tasks.register(taskName, RunGameTask.class, config).configure(t -> {
				t.setDescription("Starts the '" + config.getConfigName() + "' run configuration");

				t.dependsOn(config.getEnvironment().equals("client") ? "configureClientLaunch" : "configureLaunch");
			});
		});

		final List<String> supportedRunEnvironments = extension.getMinecraftJarConfiguration().getSupportedRunEnvironments();

		if (supportedRunEnvironments.contains("client")) {
			extension.getRunConfigs().create("client", RunConfigSettings::client);
		}

		if (supportedRunEnvironments.contains("server")) {
			extension.getRunConfigs().create("server", RunConfigSettings::server);
		}
	}
}
