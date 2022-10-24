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

package net.fabricmc.loom.configuration.processors;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.gradle.api.Project;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.processor.MappingProcessorContext;
import net.fabricmc.loom.api.processor.MinecraftJarProcessor;
import net.fabricmc.loom.api.processor.ProcessorContext;
import net.fabricmc.loom.api.processor.SpecContext;
import net.fabricmc.loom.util.ZipUtils;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

public final class MinecraftJarProcessorManager {
	private static final String CACHE_VALUE_FILE_PATH = "META-INF/Loom-Jar-Processor-Cache";

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
			MinecraftJarProcessor.Spec spec = processor.buildSpec(context);

			if (spec != null) {
				entries.add(new ProcessorEntry<>(processor, spec));
			}
		}

		if (entries.isEmpty()) {
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

	public boolean requiresProcessingJar(Path jar) {
		Objects.requireNonNull(jar);

		if (Files.notExists(jar)) {
			return true;
		}

		byte[] existingCache;

		try {
			existingCache = ZipUtils.unpackNullable(jar, CACHE_VALUE_FILE_PATH);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to unpack jar: " + jar, e);
		}

		if (existingCache == null) {
			return true;
		}

		final String existingCacheValue = new String(existingCache, StandardCharsets.UTF_8);
		return !existingCacheValue.equals(getCacheValue());
	}

	public void processJar(Path jar, ProcessorContext context) throws IOException {
		for (ProcessorEntry<?> entry : jarProcessors) {
			try {
				entry.processJar(jar, context);
			} catch (IOException e) {
				throw new IOException("Failed to process jar when running jar processor: %s".formatted(entry.name()), e);
			}
		}

		ZipUtils.add(jar, CACHE_VALUE_FILE_PATH, getCacheValue());
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
