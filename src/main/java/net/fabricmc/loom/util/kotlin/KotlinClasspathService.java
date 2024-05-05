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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Property;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.util.service.SharedService;
import net.fabricmc.loom.util.service.SharedServiceManager;

public record KotlinClasspathService(Set<URL> classpath, String version) implements KotlinClasspath, SharedService {
	public interface Spec extends SharedService.Spec {
		Property<String> getKotlinVersion();
		Property<String> getKotlinMetadataVersion();
		ConfigurableFileCollection getClasspath();

		static Optional<Spec> createIfRequired(Project project) {
			if (!KotlinPluginUtils.hasKotlinPlugin(project)) {
				return Optional.empty();
			}

			final String kotlinVersion = KotlinPluginUtils.getKotlinPluginVersion(project);
			final String kotlinMetadataVersion = KotlinPluginUtils.getKotlinMetadataVersion();

			// Create a detached config to resolve the kotlin std lib for the provided version.
			Configuration detachedConfiguration = project.getConfigurations().detachedConfiguration(
					project.getDependencies().create("org.jetbrains.kotlin:kotlin-stdlib:" + kotlinVersion),
					// Load kotlinx-metadata-jvm like this to work around: https://github.com/gradle/gradle/issues/14727
					project.getDependencies().create("org.jetbrains.kotlinx:kotlinx-metadata-jvm:" + kotlinMetadataVersion)
			);

			Spec spec = project.getObjects().newInstance(Spec.class);
			spec.getKotlinVersion().set(kotlinVersion);
			spec.getKotlinMetadataVersion().set(kotlinMetadataVersion);
			spec.getClasspath().from(detachedConfiguration);

			return Optional.of(spec);
		}

		@ApiStatus.Internal
		default String getId() {
			return "kotlinclasspath:%s:%s".formatted(getKotlinVersion().get(), getKotlinMetadataVersion().get());
		}
	}

	@Nullable
	public static KotlinClasspathService getOrCreateIfRequired(SharedServiceManager sharedServiceManager, Project project) {
		return Spec.createIfRequired(project).map(spec -> getOrCreate(sharedServiceManager, spec)).orElse(null);
	}

	public static KotlinClasspathService getOrCreate(SharedServiceManager sharedServiceManager, Spec spec) {
		Objects.requireNonNull(spec, "spec");
		return sharedServiceManager.getOrCreateService(spec.getId(), () -> create(spec.getClasspath(), spec.getKotlinVersion().get(), spec.getKotlinMetadataVersion().get()));
	}

	private static KotlinClasspathService create(FileCollection configuration, String kotlinVersion, String kotlinMetadataVersion) {
		Set<URL> classpath = configuration.getFiles().stream()
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
