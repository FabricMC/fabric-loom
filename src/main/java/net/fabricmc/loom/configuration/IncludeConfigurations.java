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
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.HasConfigurableAttributes;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.tasks.Jar;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.build.nesting.NestableJarGenerationTask;
import net.fabricmc.loom.task.AbstractRemapJarTask;
import net.fabricmc.loom.task.NestJarsAction;
import net.fabricmc.loom.task.RemapJarTask;
import net.fabricmc.loom.task.RemapTaskConfiguration;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.Strings;
import net.fabricmc.loom.util.gradle.SourceSetHelper;

/**
 * Sets up the {@code include} bucket configuration and the
 * {@code processIncludeJars} task pipeline for a given source set.
 *
 * <p>Main source set uses bare names ({@code include},
 * {@code processIncludeJars}); other source sets use their name as a prefix
 * (e.g. {@code clientInclude}, {@code processClientIncludeJars}).
 */
public final class IncludeConfigurations {
	private IncludeConfigurations() {
	}

	public static String getIncludeConfigurationName(SourceSet sourceSet) {
		return SourceSetHelper.isMainSourceSet(sourceSet)
				? Constants.Configurations.INCLUDE
				: sourceSet.getName() + Strings.capitalize(Constants.Configurations.INCLUDE);
	}

	public static String getIncludeInternalConfigurationName(SourceSet sourceSet) {
		return SourceSetHelper.isMainSourceSet(sourceSet)
				? Constants.Configurations.INCLUDE_INTERNAL
				: sourceSet.getName() + Strings.capitalize(Constants.Configurations.INCLUDE_INTERNAL);
	}

	public static String getProcessIncludeJarsTaskName(SourceSet sourceSet) {
		return sourceSet.getTaskName("process", "includeJars");
	}

	/**
	 * The conventional name of the remap-jar task that consumes this source set's
	 * include pipeline when Loom is in remap mode. For the main source set this is
	 * {@code remapJar}; for any other source set it is {@code <sourceSetName>RemapJar}
	 * (e.g. {@code clientRemapJar}).
	 */
	public static String getRemapJarTaskName(SourceSet sourceSet) {
		return SourceSetHelper.isMainSourceSet(sourceSet)
				? RemapTaskConfiguration.REMAP_JAR_TASK_NAME
				: sourceSet.getName() + Strings.capitalize(RemapTaskConfiguration.REMAP_JAR_TASK_NAME);
	}

	/**
	 * Registers the {@code include} bucket configuration and the
	 * {@code processIncludeJars} task for the given source set, and lazily wires
	 * the task output into the conventional jar/remap-jar task for the active mode.
	 */
	public static TaskProvider<NestableJarGenerationTask> setupForSourceSet(Project project, SourceSet sourceSet) {
		final ConfigurationContainer configurations = project.getConfigurations();
		final String includeName = getIncludeConfigurationName(sourceSet);
		final String includeInternalName = getIncludeInternalConfigurationName(sourceSet);
		final String taskName = getProcessIncludeJarsTaskName(sourceSet);

		final NamedDomainObjectProvider<Configuration> include = configurations.register(includeName, cfg -> {
			cfg.setCanBeConsumed(false);
			cfg.setCanBeResolved(false);
		});

		configurations.register(includeInternalName, cfg -> {
			cfg.setCanBeConsumed(false);
			cfg.setCanBeResolved(true);

			cfg.getDependencies().addAllLater(project.provider(() -> {
				List<Dependency> dependencies = new ArrayList<>();

				for (Dependency dependency : include.get().getIncoming().getDependencies()) {
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

			cfg.attributes(attributes -> {
				attributes.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
				attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.getObjects().named(LibraryElements.class, LibraryElements.JAR));
				attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, Category.LIBRARY));
				attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, project.getObjects().named(Bundling.class, Bundling.EXTERNAL));
			});
		});

		final LoomGradleExtension extension = LoomGradleExtension.get(project);
		final Provider<Configuration> internalProvider = configurations.named(includeInternalName);

		final TaskProvider<NestableJarGenerationTask> processTask = project.getTasks().register(taskName, NestableJarGenerationTask.class, task -> {
			task.from(internalProvider.get());
			task.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir(task.getName()));
			task.getUncompressNestedJars().set(extension.getUncompressNestedJars());
		});

		wireNestedJars(project, sourceSet, processTask);

		return processTask;
	}

	/**
	 * Lazily wires the {@code processIncludeJars} output into the jar task that
	 * matches the active Loom mode:
	 * <ul>
	 *     <li>{@code dontRemap} mode → the conventional Jar task for the source set
	 *         ({@link SourceSet#getJarTaskName()}).</li>
	 *     <li>Remap mode → the conventional remap-jar task name (see
	 *         {@link #getRemapJarTaskName(SourceSet)}).</li>
	 * </ul>
	 *
	 * <p>The matching is done via {@code configureEach} on the task container, so
	 * the target task can be created before or after this call.
	 */
	private static void wireNestedJars(Project project, SourceSet sourceSet, TaskProvider<NestableJarGenerationTask> processTask) {
		final LoomGradleExtension extension = LoomGradleExtension.get(project);
		final FileCollection nestedFiles = project.fileTree(processTask.flatMap(NestableJarGenerationTask::getOutputDirectory))
				.matching(pattern -> pattern.include("*.jar"));

		if (extension.dontRemapOutputs()) {
			final String jarTaskName = sourceSet.getJarTaskName();

			project.getTasks().withType(Jar.class).configureEach(task -> {
				// Avoid double-wiring: AbstractRemapJarTask extends Jar but is the remap target instead.
				if (task.getName().equals(jarTaskName) && !(task instanceof AbstractRemapJarTask)) {
					task.dependsOn(processTask);
					NestJarsAction.addToTask(task, nestedFiles);
				}
			});
		} else {
			final String remapJarTaskName = getRemapJarTaskName(sourceSet);

			project.getTasks().withType(RemapJarTask.class).configureEach(task -> {
				if (task.getName().equals(remapJarTaskName)) {
					task.getNestedJars().from(nestedFiles);
					task.getNestedJars().builtBy(processTask);
				}
			});
		}
	}
}
