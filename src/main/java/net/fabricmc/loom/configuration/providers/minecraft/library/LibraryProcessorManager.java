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

package net.fabricmc.loom.configuration.providers.minecraft.library;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.jetbrains.annotations.VisibleForTesting;

import net.fabricmc.loom.configuration.providers.minecraft.library.processors.ArmNativesLibraryProcessor;
import net.fabricmc.loom.configuration.providers.minecraft.library.processors.LWJGL2MavenLibraryProcessor;
import net.fabricmc.loom.configuration.providers.minecraft.library.processors.LWJGL3UpgradeLibraryProcessor;
import net.fabricmc.loom.configuration.providers.minecraft.library.processors.LegacyASMLibraryProcessor;
import net.fabricmc.loom.configuration.providers.minecraft.library.processors.LoomNativeSupportLibraryProcessor;
import net.fabricmc.loom.configuration.providers.minecraft.library.processors.ObjcBridgeUpgradeLibraryProcessor;
import net.fabricmc.loom.configuration.providers.minecraft.library.processors.RiscVNativesLibraryProcessor;
import net.fabricmc.loom.configuration.providers.minecraft.library.processors.RuntimeLog4jLibraryProcessor;
import net.fabricmc.loom.util.Platform;

public class LibraryProcessorManager {
	public static final List<LibraryProcessorFactory> DEFAULT_LIBRARY_PROCESSORS = List.of(
			ArmNativesLibraryProcessor::new,
			RiscVNativesLibraryProcessor::new,
			LegacyASMLibraryProcessor::new,
			LoomNativeSupportLibraryProcessor::new,
			LWJGL2MavenLibraryProcessor::new,
			LWJGL3UpgradeLibraryProcessor::new,
			ObjcBridgeUpgradeLibraryProcessor::new,
			RuntimeLog4jLibraryProcessor::new
	);

	private final Platform platform;
	private final RepositoryHandler repositories;
	private final List<LibraryProcessorFactory> libraryProcessorFactories;
	private final List<String> enabledProcessors;

	public LibraryProcessorManager(Platform platform, RepositoryHandler repositories, List<LibraryProcessorFactory> libraryProcessorFactories, List<String> enabledProcessors) {
		this.platform = platform;
		this.repositories = repositories;
		this.libraryProcessorFactories = libraryProcessorFactories;
		this.enabledProcessors = enabledProcessors;
	}

	@VisibleForTesting
	public LibraryProcessorManager(Platform platform, RepositoryHandler repositories) {
		this(platform, repositories, DEFAULT_LIBRARY_PROCESSORS, Collections.emptyList());
	}

	private List<LibraryProcessor> getProcessors(LibraryContext context) {
		var processors = new ArrayList<LibraryProcessor>();

		for (LibraryProcessorFactory factory : libraryProcessorFactories) {
			final LibraryProcessor processor = factory.apply(platform, context);
			final LibraryProcessor.ApplicationResult applicationResult = processor.getApplicationResult();

			switch (applicationResult) {
			case MUST_APPLY -> {
				processors.add(processor);
			}
			case CAN_APPLY -> {
				if (enabledProcessors.contains(processor.getClass().getSimpleName())) {
					processors.add(processor);
				}
			}
			case DONT_APPLY -> { }
			}
		}

		return Collections.unmodifiableList(processors);
	}

	public List<Library> processLibraries(List<Library> librariesIn, LibraryContext libraryContext) {
		final List<LibraryProcessor> processors = getProcessors(libraryContext);

		if (processors.isEmpty()) {
			return librariesIn;
		}

		return processLibraries(processors, librariesIn);
	}

	@VisibleForTesting
	public List<Library> processLibraries(List<LibraryProcessor> processors, List<Library> librariesIn) {
		var libraries = new ArrayList<>(librariesIn);

		for (LibraryProcessor processor : processors) {
			var processedLibraries = new ArrayList<Library>();
			final Predicate<Library> predicate = processor.apply(processedLibraries::add);

			for (Library library : libraries) {
				if (predicate.test(library)) {
					processedLibraries.add(library);
				}
			}

			processor.applyRepositories(repositories);

			libraries = processedLibraries;
		}

		return Collections.unmodifiableList(libraries);
	}

	public interface LibraryProcessorFactory extends BiFunction<Platform, LibraryContext, LibraryProcessor> {
	}
}
