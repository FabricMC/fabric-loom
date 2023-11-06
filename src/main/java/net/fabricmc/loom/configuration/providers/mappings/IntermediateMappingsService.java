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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Objects;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;
import org.gradle.api.Project;
import org.jetbrains.annotations.VisibleForTesting;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.mappings.intermediate.IntermediateMappingsProvider;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftProvider;
import net.fabricmc.loom.util.service.SharedService;
import net.fabricmc.loom.util.service.SharedServiceManager;
import net.fabricmc.mappingio.adapter.MappingNsCompleter;
import net.fabricmc.mappingio.format.tiny.Tiny2FileReader;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

public final class IntermediateMappingsService implements SharedService {
	private final Path intermediaryTiny;
	private final Supplier<MemoryMappingTree> memoryMappingTree = Suppliers.memoize(this::createMemoryMappingTree);

	private IntermediateMappingsService(Path intermediaryTiny) {
		this.intermediaryTiny = intermediaryTiny;
	}

	public static synchronized IntermediateMappingsService getInstance(SharedServiceManager sharedServiceManager, Project project, MinecraftProvider minecraftProvider) {
		final LoomGradleExtension extension = LoomGradleExtension.get(project);
		final IntermediateMappingsProvider intermediateProvider = extension.getIntermediateMappingsProvider();
		final String id = "IntermediateMappingsService:%s:%s".formatted(intermediateProvider.getName(), intermediateProvider.getMinecraftVersion().get());

		return sharedServiceManager.getOrCreateService(id, () -> create(intermediateProvider, minecraftProvider));
	}

	@VisibleForTesting
	public static IntermediateMappingsService create(IntermediateMappingsProvider intermediateMappingsProvider, MinecraftProvider minecraftProvider) {
		final Path intermediaryTiny = minecraftProvider.file(intermediateMappingsProvider.getName() + ".tiny").toPath();

		try {
			intermediateMappingsProvider.provide(intermediaryTiny);
		} catch (IOException e) {
			try {
				Files.deleteIfExists(intermediaryTiny);
			} catch (IOException ex) {
				ex.printStackTrace();
			}

			throw new UncheckedIOException("Failed to provide intermediate mappings", e);
		}

		return new IntermediateMappingsService(intermediaryTiny);
	}

	private MemoryMappingTree createMemoryMappingTree() {
		final MemoryMappingTree tree = new MemoryMappingTree();

		try {
			MappingNsCompleter nsCompleter = new MappingNsCompleter(tree, Collections.singletonMap(MappingsNamespace.NAMED.toString(), MappingsNamespace.INTERMEDIARY.toString()), true);

			try (BufferedReader reader = Files.newBufferedReader(getIntermediaryTiny(), StandardCharsets.UTF_8)) {
				Tiny2FileReader.read(reader, nsCompleter);
			}
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to read intermediary mappings", e);
		}

		return tree;
	}

	public MemoryMappingTree getMemoryMappingTree() {
		return memoryMappingTree.get();
	}

	public Path getIntermediaryTiny() {
		return Objects.requireNonNull(intermediaryTiny, "Intermediary mappings have not been setup");
	}
}
