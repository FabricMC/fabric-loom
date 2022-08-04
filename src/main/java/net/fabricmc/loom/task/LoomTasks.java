/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2022 FabricMC
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

import com.google.common.base.Preconditions;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.ide.RunConfigSettings;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftJarConfiguration;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftVersionMeta;
import net.fabricmc.loom.task.launch.GenerateDLIConfigTask;
import net.fabricmc.loom.task.launch.GenerateLog4jConfigTask;
import net.fabricmc.loom.task.launch.GenerateRemapClasspathTask;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.gradle.GradleUtils;

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
		tasks.register("generateDLIConfig", GenerateDLIConfigTask.class, t -> {
			t.setDescription("Generate the DevLaunchInjector config file");

			// Must allow these IDE files to be generated first
			t.mustRunAfter(tasks.named("eclipse"));
			t.mustRunAfter(tasks.named("idea"));
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

		TaskProvider<ValidateAccessWidenerTask> validateAccessWidener = tasks.register("validateAccessWidener", ValidateAccessWidenerTask.class, t -> {
			t.setDescription("Validate all the rules in the access widener against the Minecraft jar");
			t.setGroup("verification");
		});

		tasks.named("check").configure(task -> task.dependsOn(validateAccessWidener));

		registerIDETasks(tasks);
		registerRunTasks(tasks, project);

		// Must be done in afterEvaluate to allow time for the build script to configure the jar config.
		GradleUtils.afterSuccessfulEvaluation(project, () -> {
			LoomGradleExtension extension = LoomGradleExtension.get(project);

			if (extension.getMinecraftJarConfiguration().get() == MinecraftJarConfiguration.SERVER_ONLY) {
				// Server only, nothing more to do.
				return;
			}

			final MinecraftVersionMeta versionInfo = extension.getMinecraftProvider().getVersionInfo();

			if (versionInfo == null) {
				// Something has gone wrong, don't register the task.
				return;
			}

			registerClientSetupTasks(project.getTasks(), versionInfo.hasNativesToExtract());
		});
	}

	private static void registerIDETasks(TaskContainer tasks) {
		tasks.register("genIdeaWorkspace", GenIdeaProjectTask.class, t -> {
			t.setDescription("Generates an IntelliJ IDEA workspace from this project.");
			t.dependsOn("idea", getIDELaunchConfigureTaskName(t.getProject()));
			t.setGroup(Constants.TaskGroup.IDE);
		});

		tasks.register("genEclipseRuns", GenEclipseRunsTask.class, t -> {
			t.setDescription("Generates Eclipse run configurations for this project.");
			t.dependsOn(getIDELaunchConfigureTaskName(t.getProject()));
			t.setGroup(Constants.TaskGroup.IDE);
		});

		tasks.register("cleanEclipseRuns", CleanEclipseRunsTask.class, t -> {
			t.setDescription("Removes Eclipse run configurations for this project.");
			t.setGroup(Constants.TaskGroup.IDE);
		});

		tasks.register("vscode", GenVsCodeProjectTask.class, t -> {
			t.setDescription("Generates VSCode launch configurations.");
			t.dependsOn(getIDELaunchConfigureTaskName(t.getProject()));
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
		extension.getRunConfigs().create("client", RunConfigSettings::client);
		extension.getRunConfigs().create("server", RunConfigSettings::server);

		// Remove the client or server run config when not required. Done by name to not remove any possible custom run configs
		GradleUtils.afterSuccessfulEvaluation(project, () -> {
			String taskName = switch (extension.getMinecraftJarConfiguration().get()) {
			case SERVER_ONLY -> "client";
			case CLIENT_ONLY -> "server";
			default -> null;
			};

			if (taskName == null) {
				return;
			}

			extension.getRunConfigs().removeIf(settings -> settings.getName().equals(taskName));
		});
	}

	private static void registerClientSetupTasks(TaskContainer tasks, boolean extractNatives) {
		tasks.register("downloadAssets", DownloadAssetsTask.class, t -> {
			t.setDescription("Downloads required game assets for Minecraft.");
		});

		if (extractNatives) {
			tasks.register("extractNatives", ExtractNativesTask.class, t -> {
				t.setDescription("Extracts the Minecraft platform specific natives.");
			});
		}

		tasks.register("configureClientLaunch", task -> {
			task.dependsOn(tasks.named("downloadAssets"));
			task.dependsOn(tasks.named("configureLaunch"));

			if (extractNatives) {
				task.dependsOn(tasks.named("extractNatives"));
			}

			task.setDescription("Setup the required files to launch the Minecraft client");
			task.setGroup(Constants.TaskGroup.FABRIC);
		});
	}

	public static Provider<Task> getIDELaunchConfigureTaskName(Project project) {
		return project.provider(() -> {
			final MinecraftJarConfiguration jarConfiguration = LoomGradleExtension.get(project).getMinecraftJarConfiguration().get();
			final String name = jarConfiguration == MinecraftJarConfiguration.SERVER_ONLY ? "configureLaunch" : "configureClientLaunch";
			return project.getTasks().getByName(name);
		});
	}
}
