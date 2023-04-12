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

package net.fabricmc.loom.configuration.providers.minecraft.library.processors;

import java.util.function.Consumer;
import java.util.function.Predicate;

import net.fabricmc.loom.configuration.providers.minecraft.MinecraftVersionMeta;
import net.fabricmc.loom.configuration.providers.minecraft.library.LibraryProcessor;
import net.fabricmc.loom.configuration.providers.minecraft.library.LibraryContext;
import net.fabricmc.loom.util.Platform;

/**
 * Never depend on the legacy "asm-all".
 */
public class LegacyASMLibraryProcessor extends LibraryProcessor {
	private static final String LEGACY_ASM_PREFIX = "org.ow2.asm:asm-all";

	public LegacyASMLibraryProcessor(Platform platform, LibraryContext context) {
		super(platform, context);
	}

	@Override
	public ApplicationResult getApplicationResult() {
		// Always attempt to remove
		return ApplicationResult.MUST_APPLY;
	}

	@Override
	public Predicate<MinecraftVersionMeta.Library> apply(Consumer<Dependency> dependencyConsumer) {
		return library -> !library.name().startsWith(LEGACY_ASM_PREFIX);
	}
}
