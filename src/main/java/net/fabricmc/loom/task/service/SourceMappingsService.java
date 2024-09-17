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
import java.util.ArrayList;
import java.util.List;

import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.InputFiles;

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
		LoomGradleExtension extension = LoomGradleExtension.get(project);
		Path inputMappings = extension.getMappingConfiguration().tinyMappings;

		MemoryMappingTree mappingTree = new MemoryMappingTree();

		try (Reader reader = Files.newBufferedReader(inputMappings, StandardCharsets.UTF_8)) {
			MappingReader.read(reader, new MappingSourceNsSwitch(mappingTree, MappingsNamespace.INTERMEDIARY.toString()));
		} catch (IOException e) {
			throw new RuntimeException("Failed to read mappings", e);
		}

		final List<GenerateSourcesTask.MappingsProcessor> mappingsProcessors = new ArrayList<>();

		MinecraftJarProcessorManager minecraftJarProcessorManager = MinecraftJarProcessorManager.create(project);

		if (minecraftJarProcessorManager != null) {
			mappingsProcessors.add(mappings -> {
				try (var serviceFactory = new ScopedServiceFactory()) {
					final var configContext = new ConfigContextImpl(project, serviceFactory, extension);
					return minecraftJarProcessorManager.processMappings(mappings, new MappingProcessorContextImpl(configContext));
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});
		}

		if (mappingsProcessors.isEmpty()) {
			return inputMappings;
		}

		boolean transformed = false;

		for (GenerateSourcesTask.MappingsProcessor mappingsProcessor : mappingsProcessors) {
			if (mappingsProcessor.transform(mappingTree)) {
				transformed = true;
			}
		}

		if (!transformed) {
			return inputMappings;
		}

		final Path outputMappings;

		try {
			outputMappings = Files.createTempFile("loom-transitive-mappings", ".tiny");
		} catch (IOException e) {
			throw new RuntimeException("Failed to create temp file", e);
		}

		try (Writer writer = Files.newBufferedWriter(outputMappings, StandardCharsets.UTF_8)) {
			var tiny2Writer = new Tiny2FileWriter(writer, false);
			mappingTree.accept(new MappingSourceNsSwitch(tiny2Writer, MappingsNamespace.NAMED.toString()));
		} catch (IOException e) {
			throw new RuntimeException("Failed to write mappings", e);
		}

		return outputMappings;
	}

	public SourceMappingsService(Options options, ServiceFactory serviceFactory) {
		super(options, serviceFactory);
	}

	public Path getMappingsFile() {
		return getOptions().getMappings().getSingleFile().toPath();
	}
}
