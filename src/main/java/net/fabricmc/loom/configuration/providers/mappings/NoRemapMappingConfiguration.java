/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2026 FabricMC
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
import java.nio.file.FileSystem;
import java.nio.file.Path;

import org.gradle.api.Project;
import org.gradle.api.provider.Provider;

import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.DependencyInfo;
import net.fabricmc.loom.configuration.providers.mappings.tiny.TinyJarInfo;
import net.fabricmc.loom.configuration.providers.mappings.unpick.UnpickMetadata;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftProvider;
import net.fabricmc.loom.api.decompilers.JavadocStyle;
import net.fabricmc.loom.util.Checksum;
import net.fabricmc.loom.util.service.ServiceFactory;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

public final class NoRemapMappingConfiguration extends MappingConfiguration {
	private NoRemapMappingConfiguration(String mappingsIdentifier, Path inputJar) {
		super(mappingsIdentifier, inputJar);
	}

	public static NoRemapMappingConfiguration create(Project project, DependencyInfo dependency, MinecraftProvider minecraftProvider) {
		final String version = dependency.getResolvedVersion();
		final Path inputJar = resolveInputJar(dependency, "annotations");
		final String[] dependencyParts = dependency.getDepString().split(":");
		final String mappingsName = "annotations.%s.%s.%s".formatted(dependencyParts[0], dependencyParts[1], Checksum.of(inputJar).sha256().hex(12));
		final TinyJarInfo jarInfo = readJarInfo(inputJar, dependency, minecraftProvider, "annotations");
		final String mappingsIdentifier = createMappingsIdentifier(mappingsName, version, getMappingsClassifier(dependency, jarInfo.v2()), minecraftProvider.minecraftVersion());
		var mappingConfiguration = new NoRemapMappingConfiguration(mappingsIdentifier, inputJar);
		mappingConfiguration.setup(project, minecraftProvider, dependency, "annotations");
		return mappingConfiguration;
	}

	@Override
	protected void setupMappings(Project project, MinecraftProvider minecraftProvider, FileSystem inputJar) throws IOException {
		for (var annotationsData : getAnnotationsData()) {
			if (!MappingsNamespace.OFFICIAL.toString().equals(annotationsData.namespace())) {
				throw new IOException("Annotations patches must use the official namespace");
			}
		}

		if (hasUnpickDefinitions()
				&& getUnpickMetadata() instanceof UnpickMetadata.V2 metadata
				&& !MappingsNamespace.OFFICIAL.toString().equals(metadata.namespace())) {
			throw new IOException("Annotations unpick definitions must use the official namespace");
		}
	}

	static void validateMappings(Path mappings) throws IOException {
		MemoryMappingTree mappingTree = new MemoryMappingTree();
		MappingReader.read(mappings, mappingTree);
		validateMappings(mappingTree);
	}

	private static void validateMappings(MemoryMappingTree mappingTree) throws IOException {
		if (!MappingsNamespace.OFFICIAL.toString().equals(mappingTree.getSrcNamespace()) || !mappingTree.getDstNamespaces().isEmpty()) {
			throw new IOException("Annotations mappings must contain only the official namespace");
		}
	}

	@Override
	public TinyMappingsService getMappingsService(Project project, ServiceFactory serviceFactory) {
		TinyMappingsService mappingsService = super.getMappingsService(project, serviceFactory);

		try {
			validateMappings(mappingsService.getMappingTree());
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to validate annotations mappings", e);
		}

		return mappingsService;
	}

	@Override
	public Provider<TinyMappingsService.Options> getMappingsServiceOptions(Project project) {
		return TinyMappingsService.createOptions(project, project.provider(inputJar()::toFile), TinyJarInfo.MAPPINGS_PATH);
	}

	@Override
	public String getMappingsHash() {
		return Checksum.of(inputJar()).sha256().hex();
	}

	@Override
	public MappingsNamespace getRuntimeNamespace() {
		return MappingsNamespace.OFFICIAL;
	}

	@Override
	public JavadocStyle getJavadocStyle() {
		return JavadocStyle.MARKDOWN;
	}
}
