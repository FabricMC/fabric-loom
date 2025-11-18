/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2024-2025 FabricMC
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.mercury.Mercury;
import org.cadixdev.mercury.mixin.MixinRemapper;
import org.cadixdev.mercury.remapper.MercuryRemapper;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputDirectory;

import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.providers.mappings.TinyMappingsService;
import net.fabricmc.loom.util.DeletingFileVisitor;
import net.fabricmc.loom.util.ExceptionUtil;
import net.fabricmc.loom.util.service.Service;
import net.fabricmc.loom.util.service.ServiceFactory;
import net.fabricmc.loom.util.service.ServiceType;
import net.fabricmc.lorenztiny.TinyMappingsJoiner;

public final class MigrateSourceCodeMappingsService extends Service<MigrateSourceCodeMappingsService.Options> {
	private static final ServiceType<Options, MigrateSourceCodeMappingsService> TYPE = new ServiceType<>(Options.class, MigrateSourceCodeMappingsService.class);

	public MigrateSourceCodeMappingsService(Options options, ServiceFactory serviceFactory) {
		super(options, serviceFactory);
	}

	public interface Options extends Service.Options {
		@Nested
		Property<MigrateMappingsService.Options> getMappings();
		@InputDirectory
		DirectoryProperty getInputDir();
		@Input
		Property<String> getSourceCompatibility();
		@OutputDirectory
		DirectoryProperty getOutputDir();
	}

	public static Provider<Options> createOptions(Project project, Provider<String> targetMappings, DirectoryProperty inputDir, DirectoryProperty outputDir) {
		final JavaVersion javaVersion = project.getExtensions().getByType(JavaPluginExtension.class).getSourceCompatibility();

		return TYPE.create(project, (o) -> {
			o.getMappings().set(MigrateMappingsService.createOptions(project, targetMappings));
			o.getSourceCompatibility().set(javaVersion.toString());
			o.getInputDir().set(inputDir);
			o.getOutputDir().set(outputDir);
		});
	}

	public void migrateMappings() throws IOException {
		final Path inputDir = getOptions().getInputDir().get().getAsFile().toPath();
		final Path outputDir = getOptions().getOutputDir().get().getAsFile().toPath();

		if (!Files.exists(inputDir) || !Files.isDirectory(inputDir)) {
			throw new IllegalArgumentException("Could not find input directory: " + inputDir.toAbsolutePath());
		}

		if (Files.exists(outputDir)) {
			DeletingFileVisitor.deleteDirectory(outputDir);
		}

		Files.createDirectories(outputDir);

		Mercury mercury = new Mercury();
		mercury.setGracefulClasspathChecks(true);
		mercury.setSourceCompatibility(getOptions().getSourceCompatibility().get());

		final MigrateMappingsService migrateMappingsService = getServiceFactory().get(getOptions().getMappings());
		final MappingsService sourceMappingsService = migrateMappingsService.getSourceMappingsService();
		final TinyMappingsService targetMappingsService = migrateMappingsService.getTargetMappingsService();

		final MappingSet mappingSet = new TinyMappingsJoiner(
				sourceMappingsService.getMemoryMappingTree(), MappingsNamespace.NAMED.toString(),
				targetMappingsService.getMappingTree(), MappingsNamespace.NAMED.toString(),
				MappingsNamespace.INTERMEDIARY.toString()
		).read();

		mercury.getProcessors().add(MixinRemapper.create(mappingSet));
		mercury.getProcessors().add(MercuryRemapper.create(mappingSet));

		for (File file : migrateMappingsService.getClasspath().getFiles()) {
			mercury.getClassPath().add(file.toPath());
		}

		try {
			mercury.rewrite(
					inputDir,
					outputDir
			);
		} catch (Exception e) {
			try {
				DeletingFileVisitor.deleteDirectory(outputDir);
			} catch (IOException ignored) {
				// Nope
			}

			throw ExceptionUtil.createDescriptiveWrapper(RuntimeException::new, "Failed to migrate mappings", e);
		}

		// clean file descriptors
		System.gc();
	}
}
