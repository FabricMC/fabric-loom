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

package net.fabricmc.loom.configuration.providers.mappings;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.InputFile;

import net.fabricmc.loom.util.service.Service;
import net.fabricmc.loom.util.service.ServiceFactory;
import net.fabricmc.loom.util.service.ServiceType;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

public final class TinyMappingsService extends Service<TinyMappingsService.Options> {
	public static final ServiceType<Options, TinyMappingsService> TYPE = new ServiceType<>(Options.class, TinyMappingsService.class);

	public interface Options extends Service.Options {
		@InputFile
		RegularFileProperty getMappings();
	}

	public static Provider<Options> createOptions(Project project, Path mappings) {
		return TYPE.create(project, options -> options.getMappings().set(project.file(mappings)));
	}

	public TinyMappingsService(Options options, ServiceFactory serviceFactory) {
		super(options, serviceFactory);
	}

	private final Supplier<MemoryMappingTree> mappingTree = Suppliers.memoize(() -> {
		try {
			MemoryMappingTree mappingTree = new MemoryMappingTree();
			MappingReader.read(getOptions().getMappings().get().getAsFile().toPath(), mappingTree);
			return mappingTree;
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to read mappings", e);
		}
	});

	public MemoryMappingTree getMappingTree() {
		return mappingTree.get();
	}
}
