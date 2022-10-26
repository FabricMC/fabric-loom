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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

import org.gradle.api.Project;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.providers.mappings.MappingConfiguration;
import net.fabricmc.loom.util.TinyRemapperHelper;
import net.fabricmc.loom.util.service.SharedService;
import net.fabricmc.loom.util.service.SharedServiceManager;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.tinyremapper.IMappingProvider;

public final class MappingsService implements SharedService {
	private record Options(Path mappingsFile, String from, String to, boolean remapLocals) { }

	public static synchronized MappingsService create(SharedServiceManager sharedServiceManager, String name, Path mappingsFile, String from, String to, boolean remapLocals) {
		final Options options = new Options(mappingsFile, from, to, remapLocals);
		final String id = name + options.hashCode();
		return sharedServiceManager.getOrCreateService(id, () -> new MappingsService(options));
	}

	public static MappingsService createDefault(Project project, SharedServiceManager serviceManager, String from, String to) {
		final MappingConfiguration mappingConfiguration = LoomGradleExtension.get(project).getMappingConfiguration();

		final String name = mappingConfiguration.getBuildServiceName("mappingsProvider", from, to);
		return MappingsService.create(serviceManager, name, mappingConfiguration.tinyMappings, from, to, false);
	}

	private final Options options;

	public MappingsService(Options options) {
		this.options = options;
	}

	private IMappingProvider mappingProvider = null;
	private MemoryMappingTree memoryMappingTree = null;

	public synchronized IMappingProvider getMappingsProvider() {
		if (mappingProvider == null) {
			try {
				mappingProvider = TinyRemapperHelper.create(
						options.mappingsFile(),
						options.from(),
						options.to(),
						options.remapLocals()
				);
			} catch (IOException e) {
				throw new UncheckedIOException("Failed to read mappings from: " + options.mappingsFile(), e);
			}
		}

		return mappingProvider;
	}

	public synchronized MemoryMappingTree getMemoryMappingTree() {
		if (memoryMappingTree == null) {
			memoryMappingTree = new MemoryMappingTree();

			try {
				MappingReader.read(options.mappingsFile(), memoryMappingTree);
			} catch (IOException e) {
				throw new UncheckedIOException("Failed to read mappings from: " + options.mappingsFile(), e);
			}
		}

		return memoryMappingTree;
	}

	public String getFromNamespace() {
		return options.from();
	}

	public String getToNamespace() {
		return options.to();
	}

	@Override
	public void close() {
		mappingProvider = null;
	}
}
