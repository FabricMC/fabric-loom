/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2024-2026 FabricMC
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

import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.ConfigContextImpl;
import net.fabricmc.loom.configuration.processors.MappingProcessorContextImpl;
import net.fabricmc.loom.configuration.processors.MinecraftJarProcessorManager;
import net.fabricmc.loom.configuration.providers.mappings.MappingConfiguration;
import net.fabricmc.loom.configuration.providers.mappings.RemapMappingConfiguration;
import net.fabricmc.loom.api.decompilers.JavadocStyle;
import net.fabricmc.loom.task.GenerateSourcesTask;
import net.fabricmc.loom.util.Checksum;
import net.fabricmc.loom.util.service.ScopedServiceFactory;
import net.fabricmc.loom.util.service.Service;
import net.fabricmc.loom.util.service.ServiceFactory;
import net.fabricmc.loom.util.service.ServiceType;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.tiny.Tiny2FileWriter;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

/// Provides mappings for decompilation (MC *source* code).
/// This also works in projects with disabled obfuscation
/// where the mappings are just based on javadocs.
public class SourceMappingsService extends Service<SourceMappingsService.Options> {
	public static final ServiceType<Options, SourceMappingsService> TYPE = new ServiceType<>(Options.class, SourceMappingsService.class);
	private static final Logger LOGGER = LoggerFactory.getLogger(SourceMappingsService.class);

	public interface Options extends Service.Options {
		@InputFile
		@PathSensitive(PathSensitivity.NONE)
		RegularFileProperty getMappings();

		@Input
		@Optional
		Property<String> getProcessorHash(); // the hash of the processors applied to the mappings

		@Input
		Property<JavadocStyle> getJavadocStyle();
	}

	public static Provider<Options> create(Project project) {
		final Property<String> hash = project.getObjects().property(String.class);
		final Path mappings = getMappings(project, hash);

		return TYPE.create(project, options -> {
			options.getMappings().fileValue(project.file(mappings));
			options.getProcessorHash().set(hash);
			MappingConfiguration mappingConfiguration = LoomGradleExtension.get(project).getMappingConfigurationOrNull();
			options.getJavadocStyle().set(mappingConfiguration != null ? mappingConfiguration.getJavadocStyle() : JavadocStyle.HTML);
		});
	}

	private static Path getMappings(Project project, Property<String> hashProperty) {
		final LoomGradleExtension extension = LoomGradleExtension.get(project);
		final MinecraftJarProcessorManager jarProcessor = MinecraftJarProcessorManager.create(project);
		final Path dir = extension.getFiles().getProjectPersistentCache().toPath().resolve("source_mappings");
		final Path emptyMappingsPath = dir.resolve("empty.tiny"); // empty base mappings for unobf
		final boolean disableObf = extension.disableObfuscation();
		final MappingConfiguration mappingConfiguration = extension.getMappingConfigurationOrNull();

		if (!disableObf && mappingConfiguration == null) {
			throw new IllegalStateException("Mappings have not been configured");
		}

		if (mappingConfiguration == null && (!Files.exists(emptyMappingsPath) || extension.refreshDeps())) {
			try {
				Files.createDirectories(dir);
				Files.deleteIfExists(emptyMappingsPath);
				Files.writeString(emptyMappingsPath, "tiny\t2\t0\tofficial\n", StandardCharsets.UTF_8);
			} catch (IOException e) {
				throw new UncheckedIOException("Failed to create empty source mappings", e);
			}
		}

		final String processorHash = jarProcessor != null ? jarProcessor.getSourceMappingsHash() : "none";
		final String mappingsHash = mappingConfiguration != null
				? mappingConfiguration.getMappingsHash()
				: Checksum.of(emptyMappingsPath).sha256().hex();
		final String hash = Checksum.of(processorHash + ":" + mappingsHash).sha1().hex();
		hashProperty.set(hash);

		if (jarProcessor == null) {
			if (mappingConfiguration instanceof RemapMappingConfiguration remapMappingConfiguration) {
				LOGGER.info("No jar processor found, using configured source mappings");
				return remapMappingConfiguration.tinyMappings;
			} else if (mappingConfiguration == null) {
				LOGGER.info("No jar processor found, using empty source mappings");
				return emptyMappingsPath;
			}
		}

		final Path path = dir.resolve(hash + ".tiny");

		if (Files.exists(path) && !extension.refreshDeps()) {
			LOGGER.debug("Using cached source mappings");
			return path;
		}

		LOGGER.info("Creating source mappings for hash {}", hash);

		try {
			Files.createDirectories(dir);
			Files.deleteIfExists(path);
			createMappings(project, jarProcessor, mappingConfiguration, emptyMappingsPath, path);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to create source mappings", e);
		}

		return path;
	}

	private static void createMappings(Project project, @Nullable MinecraftJarProcessorManager jarProcessor, @Nullable MappingConfiguration mappingConfiguration, Path emptyMappings, Path outputMappings) throws IOException {
		LoomGradleExtension extension = LoomGradleExtension.get(project);
		MemoryMappingTree mappingTree = new MemoryMappingTree();
		String sourceNamespace = !extension.disableObfuscation() && extension.getUseIntermediateMappings().get()
				? MappingsNamespace.INTERMEDIARY.toString()
				: MappingsNamespace.OFFICIAL.toString();

		if (mappingConfiguration != null) {
			try (var serviceFactory = new ScopedServiceFactory()) {
				mappingConfiguration.getMappingsService(project, serviceFactory).getMappingTree()
						.accept(new MappingSourceNsSwitch(mappingTree, sourceNamespace));
			}
		} else {
			try (Reader reader = Files.newBufferedReader(emptyMappings, StandardCharsets.UTF_8)) {
				MappingReader.read(reader, new MappingSourceNsSwitch(mappingTree, sourceNamespace));
			}
		}

		if (jarProcessor != null) {
			GenerateSourcesTask.MappingsProcessor mappingsProcessor = mappings -> {
				try (var serviceFactory = new ScopedServiceFactory()) {
					final var configContext = new ConfigContextImpl(project, serviceFactory, extension);
					return jarProcessor.processMappings(mappings, new MappingProcessorContextImpl(configContext));
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			};

			boolean transformed = mappingsProcessor.transform(mappingTree);

			if (!transformed) {
				LOGGER.info("No mappings processors transformed the mappings");
			}
		}

		try (Writer writer = Files.newBufferedWriter(outputMappings, StandardCharsets.UTF_8)) {
			var tiny2Writer = new Tiny2FileWriter(writer, false);
			mappingTree.accept(new MappingSourceNsSwitch(tiny2Writer, extension.disableObfuscation() ? MappingsNamespace.OFFICIAL.toString() : MappingsNamespace.NAMED.toString()));
		}
	}

	public SourceMappingsService(Options options, ServiceFactory serviceFactory) {
		super(options, serviceFactory);
	}

	public Path getMappingsFile() {
		return getOptions().getMappings().getAsFile().get().toPath();
	}

	public @Nullable String getProcessorHash() {
		return getOptions().getProcessorHash().getOrNull();
	}

	public JavadocStyle getJavadocStyle() {
		return getOptions().getJavadocStyle().get();
	}
}
