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
import java.util.function.Supplier;

import com.google.common.base.Suppliers;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.jetbrains.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.mappings.intermediate.IntermediateMappingsProvider;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftProvider;
import net.fabricmc.loom.util.service.Service;
import net.fabricmc.loom.util.service.ServiceFactory;
import net.fabricmc.loom.util.service.ServiceType;
import net.fabricmc.mappingio.adapter.MappingNsCompleter;
import net.fabricmc.mappingio.format.tiny.Tiny2FileReader;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

public final class IntermediateMappingsService extends Service<IntermediateMappingsService.Options> {
	public static final ServiceType<Options, IntermediateMappingsService> TYPE = new ServiceType<>(Options.class, IntermediateMappingsService.class);
	private static final Logger LOGGER = LoggerFactory.getLogger(IntermediateMappingsService.class);

	public interface Options extends Service.Options {
		@InputFile
		RegularFileProperty getIntermediaryTiny();
		@Input
		Property<String> getExpectedSrcNs();
		@Input
		Property<String> getMinecraftVersion();
	}

	private final Supplier<MemoryMappingTree> memoryMappingTree = Suppliers.memoize(this::createMemoryMappingTree);

	public IntermediateMappingsService(Options options, ServiceFactory serviceFactory) {
		super(options, serviceFactory);
	}

	public static Provider<Options> createOptions(Project project, MinecraftProvider minecraftProvider) {
		final LoomGradleExtension extension = LoomGradleExtension.get(project);
		final IntermediateMappingsProvider intermediateProvider = extension.getIntermediateMappingsProvider();
		final Path intermediaryTiny = minecraftProvider.file(intermediateProvider.getName() + ".tiny").toPath();

		try {
			if (intermediateProvider instanceof IntermediateMappingsProviderInternal internal) {
				internal.provide(intermediaryTiny, project);
			} else {
				intermediateProvider.provide(intermediaryTiny);
			}
		} catch (IOException e) {
			try {
				Files.deleteIfExists(intermediaryTiny);
			} catch (IOException ex) {
				LOGGER.warn("Failed to delete intermediary mappings file", ex);
			}

			throw new UncheckedIOException("Failed to provide intermediate mappings", e);
		}

		return createOptions(project, minecraftProvider, intermediaryTiny);
	}

	private static Provider<Options> createOptions(Project project, MinecraftProvider minecraftProvider, Path intermediaryTiny) {
		final LoomGradleExtension extension = LoomGradleExtension.get(project);
		final IntermediateMappingsProvider intermediateProvider = extension.getIntermediateMappingsProvider();
		// When merging legacy versions there will be multiple named namespaces, so use intermediary as the common src ns
		// Newer versions will use intermediary as the src ns
		final String expectedSrcNs = minecraftProvider.isLegacyVersion()
				? MappingsNamespace.INTERMEDIARY.toString() // <1.3
				: MappingsNamespace.OFFICIAL.toString(); // >=1.3

		return TYPE.create(project, options -> {
			options.getIntermediaryTiny().set(intermediaryTiny.toFile());
			options.getExpectedSrcNs().set(expectedSrcNs);
			options.getMinecraftVersion().set(intermediateProvider.getMinecraftVersion());
		});
	}

	private MemoryMappingTree createMemoryMappingTree() {
		return createMemoryMappingTree(getIntermediaryTiny(), getOptions().getExpectedSrcNs().get());
	}

	@VisibleForTesting
	public static MemoryMappingTree createMemoryMappingTree(Path mappingFile, String expectedSrcNs) {
		final MemoryMappingTree tree = new MemoryMappingTree();

		try {
			MappingNsCompleter nsCompleter = new MappingNsCompleter(tree, Collections.singletonMap(MappingsNamespace.NAMED.toString(), MappingsNamespace.INTERMEDIARY.toString()), true);

			try (BufferedReader reader = Files.newBufferedReader(mappingFile, StandardCharsets.UTF_8)) {
				Tiny2FileReader.read(reader, nsCompleter);
			}
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to read intermediary mappings", e);
		}

		if (!expectedSrcNs.equals(tree.getSrcNamespace())) {
			throw new RuntimeException("Invalid intermediate mappings: expected source namespace '" + expectedSrcNs + "' but found '" + tree.getSrcNamespace() + "\'");
		}

		return tree;
	}

	public MemoryMappingTree getMemoryMappingTree() {
		return memoryMappingTree.get();
	}

	public Path getIntermediaryTiny() {
		return getOptions().getIntermediaryTiny().get().getAsFile().toPath();
	}
}
