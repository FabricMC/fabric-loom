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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.tasks.Jar;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.build.nesting.NestableJarGenerationTask;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftSourceSets;
import net.fabricmc.loom.task.service.ClientEntriesService;
import net.fabricmc.loom.task.service.JarManifestService;
import net.fabricmc.loom.util.gradle.SourceSetHelper;
import net.fabricmc.loom.util.service.ScopedServiceFactory;

/**
 * Configures the jar task for non-remapped (non-obfuscated) output.
 * Used when remapping is disabled via dontRemapOutputs().
 */
public class NonRemappedJarTaskConfiguration {
	private final Project project;
	private final LoomGradleExtension extension;
	private final TaskProvider<NestableJarGenerationTask> processIncludeJarsTask;

	public NonRemappedJarTaskConfiguration(Project project, LoomGradleExtension extension, TaskProvider<NestableJarGenerationTask> processIncludeJarsTask) {
		this.project = project;
		this.extension = extension;
		this.processIncludeJarsTask = processIncludeJarsTask;
	}

	public void configure() {
		final Provider<JarManifestService> manifestServiceProvider = JarManifestService.get(project);

		project.getTasks().named(JavaPlugin.JAR_TASK_NAME, Jar.class).configure(task -> {
			task.dependsOn(processIncludeJarsTask);

			task.doLast(new NestJarsAction(project.fileTree(processIncludeJarsTask.flatMap(NestableJarGenerationTask::getOutputDirectory))
					.matching(pattern -> pattern.include("*.jar"))));

			task.doLast(new ManifestModificationAction(
					manifestServiceProvider,
					"official",
					extension.areEnvironmentSourceSetsSplit(),
					getClientOnlyEntries()
			));

			task.usesService(manifestServiceProvider);
		});

		extension.getUnmappedModCollection().from(project.getTasks().getByName(JavaPlugin.JAR_TASK_NAME));
	}

	private List<String> getClientOnlyEntries() {
		if (!extension.areEnvironmentSourceSetsSplit()) {
			return Collections.emptyList();
		}

		final SourceSet clientSourceSet = SourceSetHelper.getSourceSetByName(
				MinecraftSourceSets.Split.CLIENT_ONLY_SOURCE_SET_NAME,
				project
		);
		final Provider<ClientEntriesService.Classes.Options> optionsProvider = ClientEntriesService.Classes.createOptions(project, clientSourceSet);

		try (var serviceFactory = new ScopedServiceFactory()) {
			ClientEntriesService<ClientEntriesService.Classes.Options> service = serviceFactory.get(optionsProvider);
			return new ArrayList<>(service.getClientOnlyEntries());
		} catch (IOException e) {
			throw new RuntimeException("Failed to determine client-only entries", e);
		}
	}
}
