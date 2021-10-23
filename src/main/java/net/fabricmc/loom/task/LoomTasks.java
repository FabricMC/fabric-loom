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

import com.google.common.base.Preconditions;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskContainer;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.decompilers.LoomDecompiler;
import net.fabricmc.loom.configuration.ide.RunConfigSettings;
import net.fabricmc.loom.configuration.providers.mappings.MappingsProviderImpl;
import net.fabricmc.loom.decompilers.fernflower.FabricFernFlowerDecompiler;
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

		tasks.register("remapJar", RemapJarTask.class, t -> {
			t.setDescription("Remaps the built project jar to intermediary mappings.");
			t.setGroup(Constants.TaskGroup.FABRIC);
		});

		tasks.register("downloadAssets", DownloadAssetsTask.class, t -> t.setDescription("Downloads required assets for Fabric."));
		tasks.register("remapSourcesJar", RemapSourcesJarTask.class, t -> t.setDescription("Remaps the project sources jar to intermediary names."));

		tasks.getByName("check").dependsOn(
				tasks.register("validateAccessWidener", ValidateAccessWidenerTask.class, t -> {
					t.setDescription("Validate all the rules in the access widener against the Minecraft jar");
					t.setGroup("verification");
				})
		);

		registerIDETasks(tasks);
		registerRunTasks(tasks, project);
		registerDecompileTasks(tasks, project);
	}

	private static void registerIDETasks(TaskContainer tasks) {
		tasks.register("genIdeaWorkspace", GenIdeaProjectTask.class, t -> {
			t.setDescription("Generates an IntelliJ IDEA workspace from this project.");
			t.dependsOn("idea", "downloadAssets");
			t.setGroup(Constants.TaskGroup.IDE);
		});

		tasks.register("genEclipseRuns", GenEclipseRunsTask.class, t -> {
			t.setDescription("Generates Eclipse run configurations for this project.");
			t.dependsOn("downloadAssets");
			t.setGroup(Constants.TaskGroup.IDE);
		});

		tasks.register("cleanEclipseRuns", CleanEclipseRunsTask.class, t -> {
			t.setDescription("Removes Eclipse run configurations for this project.");
			t.setGroup(Constants.TaskGroup.IDE);
		});

		tasks.register("vscode", GenVsCodeProjectTask.class, t -> {
			t.setDescription("Generates VSCode launch configurations.");
			t.dependsOn("downloadAssets");
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

				if (config.getEnvironment().equals("client")) {
					t.dependsOn("downloadAssets");
				}
			});
		});

		extension.getRunConfigs().create("client", RunConfigSettings::client);
		extension.getRunConfigs().create("server", RunConfigSettings::server);
	}

	private static void registerDecompileTasks(TaskContainer tasks, Project project) {
		LoomGradleExtension extension = LoomGradleExtension.get(project);

		project.afterEvaluate(p -> {
			MappingsProviderImpl mappingsProvider = extension.getMappingsProvider();

			if (mappingsProvider.mappedProvider == null) {
				// If this is ever null something has gone badly wrong,
				// for some reason for another this afterEvaluate still gets called when something has gone badly
				// wrong, returning here seems to produce nicer errors.
				return;
			}

			File inputJar = mappingsProvider.mappedProvider.getMappedJar();

			if (mappingsProvider.hasUnpickDefinitions()) {
				File outputJar = mappingsProvider.mappedProvider.getUnpickedJar();

				tasks.register("unpickJar", UnpickJarTask.class, unpickJarTask -> {
					unpickJarTask.getUnpickDefinitions().set(mappingsProvider.getUnpickDefinitionsFile());
					unpickJarTask.getInputJar().set(mappingsProvider.mappedProvider.getMappedJar());
					unpickJarTask.getOutputJar().set(outputJar);
				});

				inputJar = outputJar;
			}

			extension.getGameDecompilers().finalizeValue();

			for (LoomDecompiler decompiler : extension.getGameDecompilers().get()) {
				String taskName = decompiler instanceof FabricFernFlowerDecompiler ? "genSources" : "genSourcesWith" + decompiler.name();
				// decompiler will be passed to the constructor of GenerateSourcesTask
				GenerateSourcesTask generateSourcesTask = tasks.register(taskName, GenerateSourcesTask.class, decompiler).get();
				generateSourcesTask.getInputJar().set(inputJar);

				if (mappingsProvider.hasUnpickDefinitions()) {
					generateSourcesTask.dependsOn(tasks.getByName("unpickJar"));
				}
			}
		});
	}
}
