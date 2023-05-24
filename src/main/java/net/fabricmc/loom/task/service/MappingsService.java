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
import java.nio.file.Paths;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.gradle.api.Project;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.providers.mappings.MappingConfiguration;
import net.fabricmc.loom.util.TinyRemapperHelper;
import net.fabricmc.loom.util.service.LoomServiceSpec;
import net.fabricmc.loom.util.service.ServiceFactory;
import net.fabricmc.loom.util.service.SharedService;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.tinyremapper.IMappingProvider;

public final class MappingsService implements SharedService {
	public record Spec(String mappingsFile, String from, String to, boolean remapLocals) implements LoomServiceSpec<MappingsService> {
		@Override
		public MappingsService create(ServiceFactory serviceFactory) {
			return new MappingsService(Paths.get(mappingsFile), from, to, remapLocals);
		}

		@Override
		@JsonIgnore
		public String getCacheKey() {
			// TODO maybe include the mapping id here?
			return "mappingsProvider:%s:%s:%s:%s".formatted(
					mappingsFile,
					from,
					to,
					remapLocals
			);
		}
	}

	public static MappingsService.Spec create(Path mappingsFile, String from, String to, boolean remapLocals) {
		return new Spec(mappingsFile.toAbsolutePath().toString(), from, to, remapLocals);
	}

	public static MappingsService.Spec createDefault(Project project, String from, String to) {
		final MappingConfiguration mappingConfiguration = LoomGradleExtension.get(project).getMappingConfiguration();
		return new Spec(mappingConfiguration.tinyMappings.toAbsolutePath().toString(), from, to, false);
	}

	private final Path mappingsFile;
	private final String from;
	private final String to;
	private final boolean remapLocals;

	private MappingsService(Path mappingsFile, String from, String to, boolean remapLocals) {
		this.mappingsFile = mappingsFile;
		this.from = from;
		this.to = to;
		this.remapLocals = remapLocals;
	}

	private IMappingProvider mappingProvider = null;
	private MemoryMappingTree memoryMappingTree = null;

	public synchronized IMappingProvider getMappingsProvider() {
		if (mappingProvider == null) {
			try {
				mappingProvider = TinyRemapperHelper.create(
						mappingsFile,
						from,
						to,
						remapLocals
				);
			} catch (IOException e) {
				throw new UncheckedIOException("Failed to read mappings from: " + mappingsFile, e);
			}
		}

		return mappingProvider;
	}

	public synchronized MemoryMappingTree getMemoryMappingTree() {
		if (memoryMappingTree == null) {
			memoryMappingTree = new MemoryMappingTree();

			try {
				MappingReader.read(mappingsFile, memoryMappingTree);
			} catch (IOException e) {
				throw new UncheckedIOException("Failed to read mappings from: " + mappingsFile, e);
			}
		}

		return memoryMappingTree;
	}

	public String getFromNamespace() {
		return from;
	}

	public String getToNamespace() {
		return to;
	}

	@Override
	public void close() {
		mappingProvider = null;
	}
}
