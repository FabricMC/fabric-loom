/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2023 FabricMC
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
import java.util.Objects;

import org.cadixdev.lorenz.MappingSet;

import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.providers.mappings.MappingConfiguration;
import net.fabricmc.loom.util.service.SharedService;
import net.fabricmc.loom.util.service.SharedServiceManager;
import net.fabricmc.lorenztiny.TinyMappingsReader;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

public final class LorenzMappingService implements SharedService {
	private MappingSet mappings;

	public LorenzMappingService(MappingSet mappings) {
		this.mappings = mappings;
	}

	public static synchronized LorenzMappingService create(SharedServiceManager sharedServiceManager, MappingConfiguration mappingConfiguration, MappingsNamespace from, MappingsNamespace to) {
		return sharedServiceManager.getOrCreateService(mappingConfiguration.getBuildServiceName("LorenzMappingService", from.toString(), to.toString()), () -> {
			MemoryMappingTree m = mappingConfiguration.getMappingsService(sharedServiceManager).getMappingTree();

			try {
				try (var reader = new TinyMappingsReader(m, from.toString(), to.toString())) {
					return new LorenzMappingService(reader.read());
				}
			} catch (IOException e) {
				throw new UncheckedIOException("Failed to read lorenz mappings", e);
			}
		});
	}

	@Override
	public void close() throws IOException {
		this.mappings = null;
	}

	public MappingSet mappings() {
		return Objects.requireNonNull(mappings);
	}
}
