/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 FabricMC
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

package net.fabricmc.loom.configuration.mods.extension;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

import net.fabricmc.loom.configuration.mods.dependency.ModDependency;
import net.fabricmc.tinyremapper.InputTag;
import net.fabricmc.tinyremapper.TinyRemapper;

/**
 * An interface to aid with applying mod-specific remapping extensions.
 */
public interface ModProcessorExtension {
	List<ModProcessorExtension> EXTENSIONS = List.of(
			MixinRemap.INSTANCE,
			InlineRefmap.INSTANCE
	);

	/**
	 * Return true if the extension applies to the given mod dependency.
	 */
	boolean appliesTo(ModDependency modDependency);

	/**
	 * Create a TinyRemapper extension that uses the predicate to only apply to mods that match appliesTo.
	 */
	TinyRemapper.Extension createExtension(Context ctx, Predicate<InputTag> applyPredicate) throws IOException;

	void finalise(ModDependency modDependency, Path path) throws IOException;

	record Context(
			String from,
			String to,
			List<ModDependency> mods) { }
}
