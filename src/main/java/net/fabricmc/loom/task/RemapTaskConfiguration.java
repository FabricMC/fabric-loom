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
import java.io.Serializable;
import java.util.Arrays;

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.Directory;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.tasks.Jar;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.build.nesting.JarNester;
import net.fabricmc.loom.build.nesting.NestableJarGenerationTask;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.gradle.SyncTaskBuildService;

import org.jetbrains.annotations.NotNull;

public abstract class RemapTaskConfiguration implements Runnable {
	@Inject
	protected abstract Project getProject();

	@Inject
	protected abstract TaskContainer getTasks();

	public void run() {
		final LoomGradleExtension extension = LoomGradleExtension.get(getProject());

		SyncTaskBuildService.register(getProject());

		Configuration includeConfiguration = getProject().getConfigurations().getByName(Constants.Configurations.INCLUDE_INTERNAL);
		TaskProvider<NestableJarGenerationTask> processIncludeJarsTask = getTasks().register(Constants.Task.PROCESS_INCLUDE_JARS, NestableJarGenerationTask.class, task -> {
			task.from(includeConfiguration);
			task.getOutputDirectory().set(getProject().getLayout().getBuildDirectory().dir(task.getName()));
		});

		// Configure the jar task with JIJ support
		getTasks().named(JavaPlugin.JAR_TASK_NAME, Jar.class).configure(task -> {
			task.dependsOn(processIncludeJarsTask);
			// Use JarNester to properly add jars and update fabric.mod.json
			task.doLast(new NestJarsAction(processIncludeJarsTask.flatMap(NestableJarGenerationTask::getOutputDirectory)));
		});

		// Add jar task to unmapped collection
		extension.getUnmappedModCollection().from(getTasks().getByName(JavaPlugin.JAR_TASK_NAME));
	}

	/**
	 * Configuration-cache-compatible action for nesting jars.
	 * Uses a provider to avoid capturing task references at configuration time.
	 */
	private static class NestJarsAction implements Action<Task>, Serializable {
		private final Provider<Directory> nestedJarsDir;

		public NestJarsAction(Provider<Directory> nestedJarsDir) {
			this.nestedJarsDir = nestedJarsDir;
		}

		@Override
		public void execute(@NotNull Task t) {
			final Jar jarTask = (Jar) t;
			final File jarFile = jarTask.getArchiveFile().get().getAsFile();
			final File outputDir = nestedJarsDir.get().getAsFile();

			if (outputDir.exists() && outputDir.isDirectory()) {
				final File[] jars = outputDir.listFiles((dir, name) -> name.endsWith(".jar"));

				if (jars != null && jars.length > 0) {
					JarNester.nestJars(
							Arrays.asList(jars),
							jarFile,
							jarTask.getLogger()
					);
					jarTask.getLogger().lifecycle("Nested {} jar(s) into {}", jars.length, jarFile.getName());
				}
			}
		}
	}
}
