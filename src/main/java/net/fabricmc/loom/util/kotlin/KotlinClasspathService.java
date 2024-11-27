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
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Set;
import java.util.stream.Collectors;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;

import net.fabricmc.loom.util.service.Service;
import net.fabricmc.loom.util.service.ServiceFactory;
import net.fabricmc.loom.util.service.ServiceType;

public final class KotlinClasspathService extends Service<KotlinClasspathService.Options> implements KotlinClasspath {
	public static ServiceType<Options, KotlinClasspathService> TYPE = new ServiceType<>(Options.class, KotlinClasspathService.class);

	public interface Options extends Service.Options {
		@InputFiles
		ConfigurableFileCollection getClasspath();
		@Input
		Property<String> getKotlinVersion();
	}

	public static Provider<Options> createOptions(Project project) {
		if (!KotlinPluginUtils.hasKotlinPlugin(project)) {
			// Return an empty provider
			return project.getObjects().property(Options.class);
		}

		return createOptions(
				project,
				KotlinPluginUtils.getKotlinPluginVersion(project)
		);
	}

	private static Provider<Options> createOptions(Project project, String kotlinVersion) {
		// Create a detached config to resolve the kotlin std lib for the provided version.
		Configuration detachedConfiguration = project.getConfigurations().detachedConfiguration(
				project.getDependencies().create("org.jetbrains.kotlin:kotlin-stdlib:" + kotlinVersion),
				// Load kotlinx-metadata-jvm like this to work around: https://github.com/gradle/gradle/issues/14727
				project.getDependencies().create("org.jetbrains.kotlin:kotlin-metadata-jvm:" + kotlinVersion)
		);

		return TYPE.create(project, options -> {
			options.getClasspath().from(detachedConfiguration);
			options.getKotlinVersion().set(kotlinVersion);
		});
	}

	public KotlinClasspathService(Options options, ServiceFactory serviceFactory) {
		super(options, serviceFactory);
	}

	@Override
	public String version() {
		return getOptions().getKotlinVersion().get();
	}

	@Override
	public Set<URL> classpath() {
		return getOptions()
				.getClasspath()
				.getFiles()
				.stream()
				.map(KotlinClasspathService::fileToUrl)
				.collect(Collectors.toSet());
	}

	private static URL fileToUrl(File file) {
		try {
			return file.toURI().toURL();
		} catch (MalformedURLException e) {
			throw new UncheckedIOException(e);
		}
	}
}
