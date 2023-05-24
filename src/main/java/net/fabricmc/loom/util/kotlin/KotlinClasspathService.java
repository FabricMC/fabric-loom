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

import java.io.File;
import java.net.URI;
import java.util.Set;
import java.util.stream.Collectors;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.util.service.LoomServiceSpec;
import net.fabricmc.loom.util.service.ServiceFactory;
import net.fabricmc.loom.util.service.SharedService;

public record KotlinClasspathService(Set<URI> classpath, String version) implements KotlinClasspath, SharedService {
	public record Spec(Set<File> classpath, String kotlinVersion, String kotlinMetadataVersion) implements LoomServiceSpec<KotlinClasspathService> {
		@Override
		public KotlinClasspathService create(ServiceFactory serviceFactory) {
			return new KotlinClasspathService(
					classpath().stream().map(File::toURI).collect(Collectors.toSet()),
					kotlinVersion
			);
		}

		@Override
		public String getCacheKey() {
			return "kotlinclasspath:%s:%s".formatted(kotlinVersion, kotlinMetadataVersion);
		}
	}

	@Nullable
	public static KotlinClasspathService.Spec createIfRequired(Project project) {
		if (!KotlinPluginUtils.hasKotlinPlugin(project)) {
			return null;
		}

		return create(project, KotlinPluginUtils.getKotlinPluginVersion(project), KotlinPluginUtils.getKotlinMetadataVersion());
	}

	private static KotlinClasspathService.Spec create(Project project, String kotlinVersion, String kotlinMetadataVersion) {
		// Create a detached config to resolve the kotlin std lib for the provided version.
		Configuration detachedConfiguration = project.getConfigurations().detachedConfiguration(
				project.getDependencies().create("org.jetbrains.kotlin:kotlin-stdlib:" + kotlinVersion),
				// Load kotlinx-metadata-jvm like this to work around: https://github.com/gradle/gradle/issues/14727
				project.getDependencies().create("org.jetbrains.kotlinx:kotlinx-metadata-jvm:" + kotlinMetadataVersion)
		);

		return new KotlinClasspathService.Spec(detachedConfiguration.getFiles(), kotlinVersion, kotlinMetadataVersion);
	}
}
