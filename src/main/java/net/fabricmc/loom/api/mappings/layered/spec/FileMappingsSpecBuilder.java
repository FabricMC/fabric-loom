/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021-2022 FabricMC
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

package net.fabricmc.loom.api.mappings.layered.spec;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;

/**
 * A builder for a file mappings layer.
 * This layer type supports any mapping type that mapping-io can read.
 */
@ApiStatus.Experimental
public interface FileMappingsSpecBuilder {
	// TODO: More exhaustive tests for bareFile(), namespaces() and Enigma mappings
	/**
	 * Makes this spec read bare files instead of zips or jars.
	 *
	 * @return this builder
	 */
	default FileMappingsSpecBuilder bareFile() {
		return mappingPath(null);
	}

	/**
	 * Sets the mapping path inside a zip or jar.
	 * If the specified path is null, behaves like {@link #bareFile()}.
	 *
	 * <p>Path components within the path should be separated with {@code /}.
	 *
	 * <p>The default mapping path is {@code mappings/mappings.tiny}, matching regular mapping dependency jars
	 * such as Yarn's.
	 *
	 * @param mappingPath the mapping path, or null if a bare file
	 * @return this builder
	 */
	FileMappingsSpecBuilder mappingPath(@Nullable String mappingPath);

	/**
	 * Sets the fallback namespaces. They will be used
	 * if the mapping format itself doesn't provide namespaces with names
	 * (e.g. Enigma mappings).
	 *
	 * <p>The default fallback namespaces are {@code intermediary} as the source namespace
	 * and {@code named} as the target namespace as in Yarn.
	 *
	 * @param sourceNamespace the fallback source namespace
	 * @param targetNamespace the fallback target namespace
	 * @return this builder
	 */
	FileMappingsSpecBuilder namespaces(String sourceNamespace, String targetNamespace);

	/**
	 * Marks that the file contains Enigma mappings.
	 * Because they are stored in a directory, the format cannot be auto-detected.
	 *
	 * @return this builder
	 */
	FileMappingsSpecBuilder enigmaMappings();

	/**
	 * Marks a namespace as the source namespace of this mappings spec.
	 *
	 * <p>The source namespace is the namespace that is used to match up this layer's
	 * names to the rest of the mappings. For example, Yarn mappings should be matched up through
	 * the intermediary names.
	 *
	 * <p>The default source namespace is {@link MappingsNamespace#INTERMEDIARY}.
	 *
	 * @return this builder
	 */
	FileMappingsSpecBuilder sourceNamespace(MappingsNamespace namespace);
}
