/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2024 FabricMC
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

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.InputFiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.ConfigContextImpl;
import net.fabricmc.loom.configuration.processors.MappingProcessorContextImpl;
import net.fabricmc.loom.configuration.processors.MinecraftJarProcessorManager;
import net.fabricmc.loom.task.GenerateSourcesTask;
import net.fabricmc.loom.util.service.ScopedServiceFactory;
import net.fabricmc.loom.util.service.Service;
import net.fabricmc.loom.util.service.ServiceFactory;
import net.fabricmc.loom.util.service.ServiceType;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.tiny.Tiny2FileWriter;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

public class SourceMappingsService extends Service<SourceMappingsService.Options> {
	public static final ServiceType<Options, SourceMappingsService> TYPE = new ServiceType<>(Options.class, SourceMappingsService.class);
	private static final Logger LOGGER = LoggerFactory.getLogger(SourceMappingsService.class);

	public interface Options extends Service.Options {
		@InputFiles
		ConfigurableFileCollection getMappings(); // Only a single file
	}

	public static Provider<Options> create(Project project) {
		final Path mappings = getMappings(project);

		return TYPE.create(project, options -> {
			options.getMappings().from(project.file(mappings));
		});
	}

	private static Path getMappings(Project project) {
		final LoomGradleExtension extension = LoomGradleExtension.get(project);
		final MinecraftJarProcessorManager jarProcessor = MinecraftJarProcessorManager.create(project);

		if (jarProcessor == null) {
			LOGGER.info("No jar processor found, not creating source mappings, using project mappings");
			return extension.getMappingConfiguration().tinyMappings;
		}

		final Path dir = extension.getFiles().getProjectPersistentCache().toPath().resolve("source_mappings");
		final Path path = dir.resolve(jarProcessor.getSourceMappingsHash() + ".tiny");

		if (Files.exists(path) && !extension.refreshDeps()) {
			LOGGER.debug("Using cached source mappings");
			return path;
		}

		LOGGER.info("Creating source mappings for hash {}", jarProcessor.getSourceMappingsHash());

		try {
			Files.createDirectories(dir);
			Files.deleteIfExists(path);
			createMappings(project, jarProcessor, path);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to create source mappings", e);
		}

		return path;
	}

	private static void createMappings(Project project, MinecraftJarProcessorManager jarProcessor, Path outputMappings) throws IOException {
		LoomGradleExtension extension = LoomGradleExtension.get(project);
		Path inputMappings = extension.getMappingConfiguration().tinyMappings;
		MemoryMappingTree mappingTree = new MemoryMappingTree();

		try (Reader reader = Files.newBufferedReader(inputMappings, StandardCharsets.UTF_8)) {
			MappingReader.read(reader, new MappingSourceNsSwitch(mappingTree, MappingsNamespace.INTERMEDIARY.toString()));
		}

		GenerateSourcesTask.MappingsProcessor mappingsProcessor = mappings -> {
			try (var serviceFactory = new ScopedServiceFactory()) {
				final var configContext = new ConfigContextImpl(project, serviceFactory, extension);
				return jarProcessor.processMappings(mappings, new MappingProcessorContextImpl(configContext));
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		};

		boolean transformed = mappingsProcessor.transform(mappingTree);

		if (!transformed) {
			LOGGER.info("No mappings processors transformed the mappings");
		}

		try (Writer writer = Files.newBufferedWriter(outputMappings, StandardCharsets.UTF_8)) {
			var tiny2Writer = new Tiny2FileWriter(writer, false);
			mappingTree.accept(new MappingSourceNsSwitch(tiny2Writer, MappingsNamespace.NAMED.toString()));
		}
	}

	public SourceMappingsService(Options options, ServiceFactory serviceFactory) {
		super(options, serviceFactory);
	}

	public Path getMappingsFile() {
		return getOptions().getMappings().getSingleFile().toPath();
	}
}
