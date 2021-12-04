/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021 FabricMC
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
import java.io.UncheckedIOException;

import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.providers.mappings.MappingsProviderImpl;
import net.fabricmc.loom.util.TinyRemapperHelper;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.tinyremapper.IMappingProvider;

public abstract class MappingsService implements BuildService<MappingsService.Params>, AutoCloseable {
	interface Params extends BuildServiceParameters {
		RegularFileProperty getMappingsFile();

		Property<String> getFromNamespace();
		Property<String> getToNamespace();

		Property<Boolean> getRemapLocals();
	}

	public static synchronized Provider<MappingsService> create(Project project, String name, File mappingsFile, String from, String to, boolean remapLocals) {
		return project.getGradle().getSharedServices().registerIfAbsent(name, MappingsService.class, spec -> {
			spec.parameters(params -> {
				params.getMappingsFile().set(mappingsFile);
				params.getFromNamespace().set(from);
				params.getToNamespace().set(to);
				params.getRemapLocals().set(remapLocals);
			});
		});
	}

	public static Provider<MappingsService> createDefault(Project project, String from, String to) {
		final MappingsProviderImpl mappingsProvider = LoomGradleExtension.get(project).getMappingsProvider();
		final String name = mappingsProvider.getBuildServiceName("mappingsProvider", from, to);
		return MappingsService.create(project, name, mappingsProvider.tinyMappings.toFile(), from, to, false);
	}

	private IMappingProvider mappingProvider = null;
	private MemoryMappingTree memoryMappingTree = null;

	public synchronized IMappingProvider getMappingsProvider() {
		if (mappingProvider == null) {
			try {
				mappingProvider = TinyRemapperHelper.create(
						getParameters().getMappingsFile().get().getAsFile().toPath(),
						getParameters().getFromNamespace().get(),
						getParameters().getToNamespace().get(),
						getParameters().getRemapLocals().get()
				);
			} catch (IOException e) {
				throw new UncheckedIOException("Failed to read mappings from: " + getParameters().getMappingsFile().get().getAsFile().getAbsolutePath(), e);
			}
		}

		return mappingProvider;
	}

	public synchronized MemoryMappingTree getMemoryMappingTree() {
		if (memoryMappingTree == null) {
			memoryMappingTree = new MemoryMappingTree();

			try {
				MappingReader.read(getParameters().getMappingsFile().get().getAsFile().toPath(), memoryMappingTree);
			} catch (IOException e) {
				throw new UncheckedIOException("Failed to read mappings from: " + getParameters().getMappingsFile().get().getAsFile().getAbsolutePath(), e);
			}
		}

		return memoryMappingTree;
	}

	public String getFromNamespace() {
		return getParameters().getFromNamespace().get();
	}

	public String getToNamespace() {
		return getParameters().getToNamespace().get();
	}

	@Override
	public void close() {
		mappingProvider = null;
	}
}
