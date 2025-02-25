/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 FabricMC
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

package net.fabricmc.loom.task.service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import org.cadixdev.mercury.Mercury;
import org.cadixdev.mercury.remapper.MercuryRemapper;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;

import net.fabricmc.loom.util.service.Service;
import net.fabricmc.loom.util.service.ServiceFactory;
import net.fabricmc.loom.util.service.ServiceType;

public class MercuryService extends Service<MercuryService.Options> implements AutoCloseable {
	public static final ServiceType<MercuryService.Options, MercuryService> TYPE = new ServiceType<>(MercuryService.Options.class, MercuryService.class);

	public interface Options extends Service.Options {
		@Nested
		Property<TinyRemapperService.Options> getTinyRemapper();
		@InputFiles
		ConfigurableFileCollection getClasspath();
		@Input
		Property<Integer> getSourceCompatibility();
	}

	public static Provider<Options> createOptions(Project project,
													Provider<MappingsService.Options> mappings,
													FileCollection classpath,
													Provider<String> from,
													Provider<String> to,
													int sourceCompatibility) {
		Provider<TinyRemapperService.Options> tinyRemapper = TinyRemapperService.createOptions(
				project,
				mappings,
				classpath,
				from,
				to
		);

		return TYPE.create(project, options -> {
			options.getTinyRemapper().set(tinyRemapper);
			options.getClasspath().from(classpath);
			options.getSourceCompatibility().set(sourceCompatibility);
		});
	}

	private Mercury mercury;

	public MercuryService(Options options, ServiceFactory serviceFactory) {
		super(options, serviceFactory);
		mercury = new Mercury();
		mercury.setGracefulClasspathChecks(true);
		mercury.setSourceCompatibilityFromRelease(options.getSourceCompatibility().get());

		for (File file : options.getClasspath().getFiles()) {
			Path path = file.toPath();

			if (Files.exists(path)) {
				mercury.getClassPath().add(path);
			}
		}

		TinyRemapperService tinyRemapperService = serviceFactory.get(options.getTinyRemapper());
		mercury.getProcessors().add(MercuryRemapper.create(tinyRemapperService.getTinyRemapperForRemapping().getEnvironment()));
	}

	public Mercury getMercury() {
		return Objects.requireNonNull(mercury);
	}

	@Override
	public void close() throws Exception {
		mercury = null;
		// Yep this is correct, See: https://github.com/FabricMC/fabric-loom/issues/45
		System.gc();
	}
}
