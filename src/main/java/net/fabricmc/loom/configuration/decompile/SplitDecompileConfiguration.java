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

package net.fabricmc.loom.configuration.decompile;

import java.io.File;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskProvider;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.providers.mappings.MappingsProviderImpl;
import net.fabricmc.loom.configuration.providers.minecraft.mapped.SplitMappedMinecraftProvider;
import net.fabricmc.loom.task.GenerateSourcesTask;
import net.fabricmc.loom.task.UnpickJarTask;
import net.fabricmc.loom.util.Constants;

public final class SplitDecompileConfiguration {
	private final Project project;
	private final SplitMappedMinecraftProvider minecraftProvider;
	private final LoomGradleExtension extension;
	private final MappingsProviderImpl mappingsProvider;

	public SplitDecompileConfiguration(Project project, SplitMappedMinecraftProvider minecraftProvider) {
		this.project = project;
		this.minecraftProvider = minecraftProvider;
		this.extension = LoomGradleExtension.get(project);
		this.mappingsProvider = extension.getMappingsProvider();
	}

	public void afterEvaluation() {
		File commonJarToDecompile = minecraftProvider.getCommonJar().toFile();
		File clientOnlyJarToDecompile = minecraftProvider.getClientOnlyJar().toFile();

		TaskProvider<UnpickJarTask> unpickCommonJar = null;
		TaskProvider<UnpickJarTask> unpickClientOnlyJar = null;

		if (mappingsProvider.hasUnpickDefinitions()) {
			commonJarToDecompile = new File(extension.getMappingsProvider().mappingsWorkingDir().toFile(), "minecraft-common-unpicked.jar");
			clientOnlyJarToDecompile = new File(extension.getMappingsProvider().mappingsWorkingDir().toFile(), "minecraft-clientonly-unpicked.jar");

			unpickCommonJar = createUnpickJarTask("Common", minecraftProvider.getCommonJar().toFile(), commonJarToDecompile);
			unpickClientOnlyJar = createUnpickJarTask("ClientOnly", minecraftProvider.getClientOnlyJar().toFile(), clientOnlyJarToDecompile);
		}

		// Need to re-declare them as final to access them from the lambada
		final File commonJar = commonJarToDecompile;
		final File clientOnlyJar = clientOnlyJarToDecompile;
		final TaskProvider<UnpickJarTask> unpickCommonJarTask = unpickCommonJar;
		final TaskProvider<UnpickJarTask> unpickClientOnlyJarTask = unpickClientOnlyJar;

		final TaskProvider<Task> commonDecompileTask = createDecompileTasks("Common", task -> {
			task.getInputJar().set(commonJar);

			if (unpickCommonJarTask != null) {
				task.dependsOn(unpickCommonJarTask);
			}
		});

		final TaskProvider<Task> clientOnlyDecompileTask = createDecompileTasks("ClientOnly", task -> {
			task.getInputJar().set(clientOnlyJar);

			if (unpickCommonJarTask != null) {
				task.dependsOn(unpickClientOnlyJarTask);
			}

			// Don't allow them to run at the same time.
			task.mustRunAfter(commonDecompileTask);
		});

		project.getTasks().register("genSources", task -> {
			task.setDescription("Decompile minecraft using the default decompiler.");
			task.setGroup(Constants.TaskGroup.FABRIC);

			task.dependsOn(commonDecompileTask);
			task.dependsOn(clientOnlyDecompileTask);
		});
	}

	private TaskProvider<UnpickJarTask> createUnpickJarTask(String name, File inputJar, File outputJar) {
		return project.getTasks().register("unpick%sJar".formatted(name), UnpickJarTask.class, unpickJarTask -> {
			unpickJarTask.getUnpickDefinitions().set(mappingsProvider.getUnpickDefinitionsFile());
			unpickJarTask.getInputJar().set(inputJar);
			unpickJarTask.getOutputJar().set(outputJar);
		});
	}

	private TaskProvider<Task> createDecompileTasks(String name, Action<GenerateSourcesTask> configureAction) {
		extension.getGameDecompilers().forEach(decompiler -> {
			final String taskName = "gen%sSourcesWith%s".formatted(name, decompiler.name());

			project.getTasks().register(taskName, GenerateSourcesTask.class, decompiler).configure(task -> {
				configureAction.execute(task);
				task.dependsOn(project.getTasks().named("validateAccessWidener"));
				task.setDescription("Decompile minecraft using %s.".formatted(decompiler.name()));
				task.setGroup(Constants.TaskGroup.FABRIC);
			});
		});

		return project.getTasks().register("gen%sSources".formatted(name), task -> {
			task.setDescription("Decompile minecraft (%s) using the default decompiler.".formatted(name));
			task.setGroup(Constants.TaskGroup.FABRIC);

			task.dependsOn(project.getTasks().named("gen%sSourcesWithCfr".formatted(name)));
		});
	}
}
