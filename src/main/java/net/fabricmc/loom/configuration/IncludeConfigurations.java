/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2026 FabricMC
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

package net.fabricmc.loom.configuration;

import java.util.ArrayList;
import java.util.List;

import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.HasConfigurableAttributes;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.tasks.Jar;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.build.nesting.NestableJarGenerationTask;
import net.fabricmc.loom.task.NestJarsAction;
import net.fabricmc.loom.task.RemapJarTask;
import net.fabricmc.loom.util.Strings;

/**
 * Sets up include configurations for the jar task that consumes them.
 */
public final class IncludeConfigurations {
	private IncludeConfigurations() {
	}

	public static void nestJars(Project project, TaskProvider<? extends Jar> jarTask, Configuration configuration) {
		String taskName = getUniqueTaskName(project, getProcessIncludeJarsTaskName(jarTask.getName(), configuration.getName()));
		nestJars(project, jarTask, project.provider(() -> configuration), taskName);
	}

	public static void nestJars(Project project, TaskProvider<? extends Jar> jarTask, NamedDomainObjectProvider<? extends Configuration> configuration) {
		String taskName = getUniqueTaskName(project, getProcessIncludeJarsTaskName(jarTask.getName(), configuration.getName()));
		nestJars(project, jarTask, configuration, taskName);
	}

	public static void nestJars(Project project, TaskProvider<? extends Jar> jarTask, Configuration configuration, String taskName) {
		nestJars(project, jarTask, project.provider(() -> configuration), taskName);
	}

	public static void nestJars(Project project, TaskProvider<? extends Jar> jarTask, Provider<? extends Configuration> configuration, String taskName) {
		TaskProvider<NestableJarGenerationTask> processTask = createProcessTask(project, configuration, taskName);
		FileCollection outputJars = getOutputJars(project, processTask);

		jarTask.configure(task -> {
			task.dependsOn(processTask);

			if (task instanceof RemapJarTask remapJarTask) {
				remapJarTask.getNestedJars().from(outputJars);
				remapJarTask.getNestedJars().builtBy(processTask);
			} else {
				NestJarsAction.addToTask(task, outputJars);
			}
		});
	}

	private static TaskProvider<NestableJarGenerationTask> createProcessTask(Project project, Provider<? extends Configuration> configuration, String taskName) {
		Configuration internalConfiguration = createInternalConfiguration(project, configuration);
		LoomGradleExtension extension = LoomGradleExtension.get(project);

		return project.getTasks().register(taskName, NestableJarGenerationTask.class, task -> {
			task.from(internalConfiguration);
			task.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir(task.getName()));
			task.getUncompressNestedJars().set(extension.getUncompressNestedJars());
		});
	}

	private static Configuration createInternalConfiguration(Project project, Provider<? extends Configuration> include) {
		Configuration internal = project.getConfigurations().detachedConfiguration();
		LoomConfigurations.Role.RESOLVABLE.apply(internal);
		addNonTransitiveDependencies(project, internal, include);
		configureAttributes(project, internal);
		return internal;
	}

	private static void addNonTransitiveDependencies(Project project, Configuration target, Provider<? extends Configuration> source) {
		target.getDependencies().addAllLater(project.provider(() -> {
			List<Dependency> dependencies = new ArrayList<>();

			for (Dependency dependency : source.get().getIncoming().getDependencies()) {
				if (dependency instanceof HasConfigurableAttributes<?> hasAttributes) {
					Category category = hasAttributes.getAttributes().getAttribute(Category.CATEGORY_ATTRIBUTE);

					if (category != null && (category.getName().equals(Category.ENFORCED_PLATFORM) || category.getName().equals(Category.REGULAR_PLATFORM))) {
						dependencies.add(dependency);
						continue;
					} else if (dependency instanceof ModuleDependency moduleDependency) {
						ModuleDependency copy = moduleDependency.copy();
						copy.setTransitive(false);
						dependencies.add(copy);
						continue;
					}
				}

				dependencies.add(dependency);
			}

			return dependencies;
		}));
	}

	private static void configureAttributes(Project project, Configuration configuration) {
		configuration.attributes(attributes -> {
			attributes.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
			attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.getObjects().named(LibraryElements.class, LibraryElements.JAR));
			attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, Category.LIBRARY));
			attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, project.getObjects().named(Bundling.class, Bundling.EXTERNAL));
		});
	}

	private static FileCollection getOutputJars(Project project, TaskProvider<NestableJarGenerationTask> processTask) {
		return project.fileTree(processTask.flatMap(NestableJarGenerationTask::getOutputDirectory))
				.matching(pattern -> pattern.include("*.jar"));
	}

	private static String getProcessIncludeJarsTaskName(String jarTaskName, String configurationName) {
		return "process" + Strings.capitalize(jarTaskName) + Strings.capitalize(configurationName) + "Jars";
	}

	private static String getUniqueTaskName(Project project, String taskName) {
		if (!project.getTasks().getNames().contains(taskName)) {
			return taskName;
		}

		int suffix = 2;

		while (project.getTasks().getNames().contains(taskName + suffix)) {
			suffix++;
		}

		return taskName + suffix;
	}
}
