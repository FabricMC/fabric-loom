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
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.providers.mappings.MappingsProviderImpl;
import net.fabricmc.loom.task.service.TinyRemapperMappingsService;
import net.fabricmc.loom.task.service.TinyRemapperService;
import net.fabricmc.loom.util.Constants;

public class RemapTaskConfiguration {
	private static final String REMAP_JAR_TASK_NAME = "remapJar";

	public static void setupRemap(Project project) {
		TaskContainer tasks = project.getTasks();

		// Register the default remap jar task
		TaskProvider<RemapJarTask> remapJarTaskProvider = tasks.register(REMAP_JAR_TASK_NAME, RemapJarTask.class, task -> {
			final AbstractArchiveTask jarTask = tasks.named(JavaPlugin.JAR_TASK_NAME, AbstractArchiveTask.class).get();

			// Basic task setup
			task.dependsOn(jarTask);
			task.setDescription("Remaps the built project jar to intermediary mappings.");
			task.setGroup(Constants.TaskGroup.FABRIC);
			project.getArtifacts().add("archives", task);

			// Setup the input file and the nested deps
			task.getInputFile().convention(jarTask.getArchiveFile());
			task.getNestedJars().plus(project.getConfigurations().getByName(Constants.Configurations.INCLUDE));

			// Setup the remapper service
			final FileCollection classpath = project.getConfigurations().getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME);

			task.getTinyRemapperBuildService().convention(getOrCreateTinyRemapperBuildServiceProvider(project,
					task.getSourceNamespace(),
					task.getTargetNamespace(),
					classpath
			));
		});

		// Configure the default jar task
		tasks.named(JavaPlugin.JAR_TASK_NAME, AbstractArchiveTask.class).configure(task -> {
			task.getArchiveClassifier().convention("dev");
			task.finalizedBy(remapJarTaskProvider);
		});
	}

	private static Provider<TinyRemapperService> getOrCreateTinyRemapperBuildServiceProvider(final Project project, final Provider<String> from, final Provider<String> to, final FileCollection classpath) {
		final LoomGradleExtension extension = LoomGradleExtension.get(project);
		final MappingsProviderImpl mappingsProvider = extension.getMappingsProvider();

		// This should give us what shared caches did before but for free, and safely. -- TODO take into account the classpath?
		final String name = "remapJarService:%s:%s>%S".formatted(mappingsProvider.mappingsIdentifier(), from, to);

		return project.getGradle().getSharedServices().registerIfAbsent(name, TinyRemapperService.class, spec -> {
			spec.parameters(params -> {
				params.getClasspath().plus(classpath);
				params.getMappings().add(TinyRemapperMappingsService.create(project, mappingsProvider.tinyMappings.toFile(), from.get(), to.get(), false));

				boolean legacyMixin = extension.getMixin().getUseLegacyMixinAp().get();
				params.getUseMixinExtension().set(!legacyMixin);

				if (legacyMixin) {
					// Add the mapping from the mixin AP
					for (File file : extension.getAllMixinMappings().getFiles()) {
						params.getMappings().add(TinyRemapperMappingsService.create(project, file, from.get(), to.get(), false));
					}
				}
			});
		});
	}
}
