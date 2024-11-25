/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022-2023 FabricMC
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

package net.fabricmc.loom.configuration.processors;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import org.gradle.api.Project;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.processor.MappingProcessorContext;
import net.fabricmc.loom.api.processor.MinecraftJarProcessor;
import net.fabricmc.loom.api.processor.ProcessorContext;
import net.fabricmc.loom.api.processor.SpecContext;
import net.fabricmc.loom.util.Checksum;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

public final class MinecraftJarProcessorManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(MinecraftJarProcessorManager.class);

	private final List<ProcessorEntry<?>> jarProcessors;

	private MinecraftJarProcessorManager(List<ProcessorEntry<?>> jarProcessors) {
		this.jarProcessors = Collections.unmodifiableList(jarProcessors);
	}

	@Nullable
	public static MinecraftJarProcessorManager create(Project project) {
		final LoomGradleExtension extension = LoomGradleExtension.get(project);
		List<MinecraftJarProcessor<?>> processors = new ArrayList<>(extension.getMinecraftJarProcessors().get());

		for (JarProcessor legacyProcessor : extension.getGameJarProcessors().get()) {
			processors.add(project.getObjects().newInstance(LegacyJarProcessorWrapper.class, legacyProcessor));
		}

		return MinecraftJarProcessorManager.create(processors, SpecContextImpl.create(project));
	}

	@Nullable
	public static MinecraftJarProcessorManager create(List<MinecraftJarProcessor<?>> processors, SpecContext context) {
		List<ProcessorEntry<?>> entries = new ArrayList<>();

		for (MinecraftJarProcessor<?> processor : processors) {
			LOGGER.debug("Building processor spec for {}", processor.getName());
			MinecraftJarProcessor.Spec spec = processor.buildSpec(context);

			if (spec != null) {
				LOGGER.debug("Adding processor entry for {}", processor.getName());
				entries.add(new ProcessorEntry<>(processor, spec));
			}
		}

		if (entries.isEmpty()) {
			LOGGER.debug("No processor entries");
			return null;
		}

		return new MinecraftJarProcessorManager(entries);
	}

	private String getCacheValue() {
		return jarProcessors.stream()
				.sorted(Comparator.comparing(ProcessorEntry::name))
				.map(ProcessorEntry::cacheValue)
				.collect(Collectors.joining("::"));
	}

	private String getDebugString() {
		final var sj = new StringJoiner("\n");

		for (ProcessorEntry<?> jarProcessor : jarProcessors) {
			sj.add(jarProcessor.name() + ":");
			sj.add("\tHash: " + jarProcessor.hashCode());
			sj.add("\tStr: " + jarProcessor.cacheValue());
		}

		return sj.toString();
	}

	public String getJarHash() {
		//fabric-loom:mod-javadoc:-1289977000
		return Checksum.sha1Hex(getCacheValue().getBytes(StandardCharsets.UTF_8)).substring(0, 10);
	}

	public String getSourceMappingsHash() {
		return Checksum.sha1Hex(getCacheValue().getBytes(StandardCharsets.UTF_8));
	}

	public boolean requiresProcessingJar(Path jar) {
		Objects.requireNonNull(jar);

		if (Files.notExists(jar)) {
			LOGGER.debug("{} does not exist, generating", jar);
			return true;
		}

		return false;
	}

	public void processJar(Path jar, ProcessorContext context) throws IOException {
		for (ProcessorEntry<?> entry : jarProcessors) {
			try {
				entry.processJar(jar, context);
			} catch (IOException e) {
				throw new IOException("Failed to process jar when running jar processor: %s".formatted(entry.name()), e);
			}
		}
	}

	public boolean processMappings(MemoryMappingTree mappings, MappingProcessorContext context) {
		boolean transformed = false;

		for (ProcessorEntry<?> entry : jarProcessors) {
			if (entry.processMappings(mappings, context)) {
				transformed = true;
			}
		}

		return transformed;
	}

	record ProcessorEntry<S extends MinecraftJarProcessor.Spec>(S spec, MinecraftJarProcessor<S> processor, @Nullable MinecraftJarProcessor.MappingsProcessor<S> mappingsProcessor) {
		@SuppressWarnings("unchecked")
		ProcessorEntry(MinecraftJarProcessor<?> processor, MinecraftJarProcessor.Spec spec) {
			this((S) Objects.requireNonNull(spec), (MinecraftJarProcessor<S>) processor, (MinecraftJarProcessor.MappingsProcessor<S>) processor.processMappings());
		}

		private void processJar(Path jar, ProcessorContext context) throws IOException {
			processor().processJar(jar, spec, context);
		}

		private boolean processMappings(MemoryMappingTree mappings, MappingProcessorContext context) {
			if (mappingsProcessor() == null) {
				return false;
			}

			return mappingsProcessor().transform(mappings, spec, context);
		}

		private String name() {
			return processor.getName();
		}

		private String cacheValue() {
			return processor.getName() + ":" + spec.hashCode();
		}
	}
}
