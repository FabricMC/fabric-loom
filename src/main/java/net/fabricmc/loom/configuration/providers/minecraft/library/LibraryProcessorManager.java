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

import net.fabricmc.loom.configuration.providers.minecraft.MinecraftVersionMeta;
import net.fabricmc.loom.configuration.providers.minecraft.library.processors.ArmNativesLibraryProcessor;
import net.fabricmc.loom.configuration.providers.minecraft.library.processors.LWJGL2MavenLibraryProcessor;
import net.fabricmc.loom.configuration.providers.minecraft.library.processors.LWJGL3UpgradeLibraryProcessor;
import net.fabricmc.loom.configuration.providers.minecraft.library.processors.LegacyASMLibraryProcessor;
import net.fabricmc.loom.configuration.providers.minecraft.library.processors.LoomNativeSupportLibraryProcessor;
import net.fabricmc.loom.configuration.providers.minecraft.library.processors.ObjcBridgeUpgradeLibraryProcessor;
import net.fabricmc.loom.configuration.providers.minecraft.library.processors.RuntimeLog4jLibraryProcessor;
import net.fabricmc.loom.util.Platform;

public class LibraryProcessorManager {
	private static final List<LibraryProcessorFactory> LIBRARY_PROCESSORS = List.of(
			ArmNativesLibraryProcessor::new,
			LegacyASMLibraryProcessor::new,
			LoomNativeSupportLibraryProcessor::new,
			LWJGL2MavenLibraryProcessor::new,
			LWJGL3UpgradeLibraryProcessor::new,
			ObjcBridgeUpgradeLibraryProcessor::new,
			RuntimeLog4jLibraryProcessor::new
	);

	private final Platform platform;

	public LibraryProcessorManager(Platform platform) {
		this.platform = platform;
	}

	public List<LibraryProcessor> getProcessors(LibraryContext context) {
		var processors = new ArrayList<LibraryProcessor>();

		for (LibraryProcessorFactory factory : LIBRARY_PROCESSORS) {
			final LibraryProcessor processor = factory.apply(platform, context);
			final LibraryProcessor.ApplicationResult applicationResult = processor.getApplicationResult();

			switch (applicationResult) {
			case MUST_APPLY -> {
				processors.add(processor);
			}
			case CAN_APPLY -> {
				// TODO check gradle property to enable
				if (false) {
					processors.add(processor);
				}
			}
			case DONT_APPLY -> { }
			}
		}

		return Collections.unmodifiableList(processors);
	}

	public LibraryResult processLibraries(MinecraftVersionMeta versionMeta, LibraryContext libraryContext) {
		final List<LibraryProcessor> processors = getProcessors(libraryContext);

		if (processors.isEmpty()) {
			return new LibraryResult(versionMeta.libraries(), Collections.emptyList());
		}

		var dependencies = new ArrayList<LibraryProcessor.Dependency>();
		final var libraryPredicate = processors.stream()
				.map(processor -> processor.apply(dependencies::add))
				.reduce(LibraryProcessor.ALLOW_ALL, Predicate::and);
		var libraries = versionMeta.libraries().stream()
				.filter(library -> library.isValidForOS(platform))
				.filter(libraryPredicate).toList();
		return new LibraryResult(libraries, Collections.unmodifiableList(dependencies));
	}

	interface LibraryProcessorFactory extends BiFunction<Platform, LibraryContext, LibraryProcessor> {
	}

	/**
	 * @param libraries The processed Minecraft libraries
	 * @param dependencies Additional deps added by processors
	 */
	record LibraryResult(List<MinecraftVersionMeta.Library> libraries, List<LibraryProcessor.Dependency> dependencies) { }
}
