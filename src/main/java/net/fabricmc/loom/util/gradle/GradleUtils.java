/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022 FabricMC
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

package net.fabricmc.loom.util.gradle;

import java.io.File;
import java.lang.reflect.Field;
import java.util.function.Consumer;

import org.gradle.api.Project;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency;
import org.gradle.api.internal.catalog.DelegatingProjectDependency;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.provider.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GradleUtils {
	private static final Logger LOGGER = LoggerFactory.getLogger(GradleUtils.class);

	private GradleUtils() {
	}

	// For some crazy reason afterEvaluate is still invoked when the configuration fails
	public static void afterSuccessfulEvaluation(Project project, Runnable afterEvaluate) {
		project.afterEvaluate(p -> {
			if (p.getState().getFailure() != null) {
				// Let gradle handle the failure
				return;
			}

			afterEvaluate.run();
		});
	}

	public static void allLoomProjects(Gradle gradle, Consumer<Project> consumer) {
		gradle.allprojects(project -> {
			if (isLoomProject(project)) {
				consumer.accept(project);
			}
		});
	}

	public static boolean isLoomProject(Project project) {
		return project.getPluginManager().hasPlugin("fabric-loom");
	}

	public static Provider<Boolean> getBooleanPropertyProvider(Project project, String key) {
		// Works around https://github.com/gradle/gradle/issues/23572
		return project.provider(() -> {
			final Object value = project.findProperty(key);

			if (value instanceof String str) {
				try {
					return Boolean.parseBoolean(str);
				} catch (final IllegalArgumentException ex) {
					return false;
				}
			} else {
				return false;
			}
		});
	}

	public static boolean getBooleanProperty(Project project, String key) {
		return getBooleanPropertyProvider(project, key).getOrElse(false);
	}

	// A hack to include the given file in the configuration cache input
	// this ensures that configuration cache is invalidated when the file changes
	public static File configurationInputFile(Project project, File file) {
		final RegularFileProperty property = project.getObjects().fileProperty();
		property.set(file);
		return property.getAsFile().get();
	}

	// Get the project from the field with reflection to suppress the deprecation warning.
	// If you hate it find a solution yourself and make a PR, I'm getting a bit tired of chasing Gradle updates
	public static Project getDependencyProject(ProjectDependency projectDependency) {
		if (projectDependency instanceof DefaultProjectDependency) {
			try {
				final Class<DefaultProjectDependency> clazz = DefaultProjectDependency.class;
				final Field dependencyProject = clazz.getDeclaredField("dependencyProject");
				dependencyProject.setAccessible(true);
				return (Project) dependencyProject.get(projectDependency);
			} catch (NoSuchFieldException | IllegalAccessException e) {
				LOGGER.warn("Failed to reflect DefaultProjectDependency", e);
			}
		} else if (projectDependency instanceof DelegatingProjectDependency) {
			try {
				final Class<DelegatingProjectDependency> clazz = DelegatingProjectDependency.class;
				final Field delgeate = clazz.getDeclaredField("delegate");
				delgeate.setAccessible(true);
				return getDependencyProject((ProjectDependency) delgeate.get(projectDependency));
			} catch (NoSuchFieldException | IllegalAccessException e) {
				LOGGER.warn("Failed to reflect DelegatingProjectDependency", e);
			}
		}

		// Just fallback and trigger the warning, this will break in Gradle 9
		final Project project = projectDependency.getDependencyProject();
		LOGGER.warn("Loom was unable to suppress the deprecation warning for ProjectDependency#getDependencyProject, if you are on the latest version of Loom please report this issue to the Loom developers and provide the error above, this WILL stop working in a future Gradle version.");
		return project;
	}
}
