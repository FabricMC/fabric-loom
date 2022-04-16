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

package net.fabricmc.loom.util.kotlin;

import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Set;
import java.util.stream.Collectors;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.util.service.SharedService;
import net.fabricmc.loom.util.service.SharedServiceManager;

public record KotlinClasspathService(Set<URL> classpath, String version) implements KotlinClasspath, SharedService {
	@Nullable
	public static KotlinClasspathService getOrCreateIfRequired(Project project) {
		if (!KotlinPluginUtils.hasKotlinPlugin(project)) {
			return null;
		}

		return getOrCreate(project, KotlinPluginUtils.getKotlinPluginVersion(project));
	}

	public static synchronized KotlinClasspathService getOrCreate(Project project, String kotlinVersion) {
		final String id = "kotlinclasspath:" + kotlinVersion;
		final SharedServiceManager sharedServiceManager = SharedServiceManager.get(project);
		return sharedServiceManager.getOrCreateService(id, () -> create(project, kotlinVersion));
	}

	private static KotlinClasspathService create(Project project, String kotlinVersion) {
		// Create a detached config to resolve the koltin std lib for the provided version.
		Configuration detachedConfiguration = project.getConfigurations().detachedConfiguration(
				project.getDependencies().create("org.jetbrains.kotlin:kotlin-stdlib:" + kotlinVersion)
		);

		Set<URL> classpath = detachedConfiguration.getFiles().stream()
				.map(file -> {
					try {
						return file.toURI().toURL();
					} catch (MalformedURLException e) {
						throw new UncheckedIOException(e);
					}
				}).collect(Collectors.toSet());;

		return new KotlinClasspathService(classpath, kotlinVersion);
	}
}
