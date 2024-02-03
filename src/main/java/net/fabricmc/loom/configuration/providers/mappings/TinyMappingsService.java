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

import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.util.service.SharedService;
import net.fabricmc.loom.util.service.SharedServiceManager;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

public final class TinyMappingsService implements SharedService {
	private final String defaultSrcNs;

	private MemoryMappingTree mappingTree;
	private String lastSrcNs;

	public TinyMappingsService(Path tinyMappings) {
		try {
			this.mappingTree = new MemoryMappingTree();
			MappingReader.read(tinyMappings, mappingTree);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to read mappings", e);
		}

		this.defaultSrcNs = this.mappingTree.getSrcNamespace();
		this.lastSrcNs = this.defaultSrcNs;
	}

	public static synchronized TinyMappingsService create(SharedServiceManager serviceManager, Path tinyMappings) {
		return serviceManager.getOrCreateService("TinyMappingsService:" + tinyMappings.toAbsolutePath(), () -> new TinyMappingsService(tinyMappings));
	}

	public MemoryMappingTree getMappingTree() {
		return getMappingTree(defaultSrcNs);
	}

	public MemoryMappingTree getMappingTree(MappingsNamespace srcNs) {
		return getMappingTree(srcNs.toString());
	}

	public MemoryMappingTree getMappingTree(String srcNs) {
		if (!srcNs.equals(lastSrcNs)) {
			MemoryMappingTree tree = new MemoryMappingTree();

			try {
				mappingTree.accept(new MappingSourceNsSwitch(tree, srcNs));
			} catch (IOException e) {
				throw new UncheckedIOException("Failed to switch source namespace", e);
			}

			mappingTree = tree;
			lastSrcNs = srcNs;
		}

		return mappingTree;
	}
}
