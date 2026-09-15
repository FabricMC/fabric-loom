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

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.provider.HasMultipleValues;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.jspecify.annotations.Nullable;

import net.fabricmc.loom.task.RemapJarTask;
import net.fabricmc.loom.task.RemapTaskConfiguration;
import net.fabricmc.loom.util.Constants;

/**
 * Optional integration with Shadow, without linking Loom against Shadow's API.
 */
public final class ShadowConfiguration {
	private static final List<String> PLUGIN_IDS = List.of("com.gradleup.shadow", "com.github.johnrengelman.shadow");

	private ShadowConfiguration() {
	}

	public static void setup(final Project project) {
		final var configured = new AtomicBoolean();

		for (final var pluginId : PLUGIN_IDS) {
			project.getPluginManager().withPlugin(pluginId, ignored -> {
				if (configured.compareAndSet(false, true)) configure(project);
			});
		}
	}

	private static void configure(final Project project) {
		final var shadowRuntime = project.getConfigurations().getByName(Constants.Configurations.SHADOW_RUNTIME_CLASSPATH);
		final var shadowJar = project.getTasks().findByName("shadowJar");

		if (!(shadowJar instanceof final AbstractArchiveTask archiveTask)) {
			throw new GradleException("Shadow plugin's conventional shadowJar task is missing or is not an archive task; this Shadow version is not supported by Loom");
		}

		configureConfigurations(project, shadowJar, shadowRuntime);

		final var remapTask = project.getTasks().findByName(RemapTaskConfiguration.REMAP_JAR_TASK_NAME);

		if (remapTask instanceof final RemapJarTask remapJar) {
			remapJar.getInputFile().convention(archiveTask.getArchiveFile());
		}
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static void configureConfigurations(final Project project, final Task shadowJar, final Configuration shadowRuntime) {
		final Object configurations;

		try {
			configurations = shadowJar.getClass().getMethod("getConfigurations").invoke(shadowJar);
		} catch (final NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
			throw unsupported(e);
		}

		if (configurations instanceof final HasMultipleValues values) {
			values.convention(List.of(shadowRuntime));
			return;
		}

		// Shadow 8 and older expose a mutable/list-backed bean property.
		// At plugin application time its singleton runtimeClasspath value is Shadow's default;
		// any other value is an explicit user choice and must be retained.
		if (configurations instanceof final Collection<?> collection) {
			final var runtimeClasspath = project.getConfigurations().getByName("runtimeClasspath");

			if (collection.size() != 1 || collection.iterator().next() != runtimeClasspath) return;

			try {
				final var setter = shadowJar.getClass().getMethod("setConfigurations", List.class);
				setter.invoke(shadowJar, List.of(shadowRuntime));
				return;
			} catch (final NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
				throw unsupported(e);
			}
		}

		throw unsupported(null);
	}

	private static GradleException unsupported(@Nullable final Throwable cause) {
		return new GradleException("Unsupported Shadow task API: Loom cannot safely configure shadowJar.configurations", cause);
	}
}
