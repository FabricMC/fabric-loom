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

package net.fabricmc.loom.configuration.providers.mappings.file;

import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.api.mappings.layered.spec.FileMappingsSpecBuilder;
import net.fabricmc.loom.api.mappings.layered.spec.FileSpec;
import net.fabricmc.mappingio.format.MappingFormat;

public class FileMappingsSpecBuilderImpl implements FileMappingsSpecBuilder {
	private static final String DEFAULT_MAPPING_PATH = "mappings/mappings.tiny";
	private static final String DEFAULT_FALLBACK_SOURCE_NAMESPACE = MappingsNamespace.INTERMEDIARY.toString();
	private static final String DEFAULT_FALLBACK_TARGET_NAMESPACE = MappingsNamespace.NAMED.toString();
	private final FileSpec fileSpec;
	private @Nullable String mappingPath = DEFAULT_MAPPING_PATH;
	private String fallbackSourceNamespace = DEFAULT_FALLBACK_SOURCE_NAMESPACE;
	private String fallbackTargetNamespace = DEFAULT_FALLBACK_TARGET_NAMESPACE;
	private @Nullable MappingFormat mappingFormat = null;
	private MappingsNamespace sourceNamespace = MappingsNamespace.INTERMEDIARY;

	private FileMappingsSpecBuilderImpl(FileSpec fileSpec) {
		this.fileSpec = fileSpec;
	}

	public static FileMappingsSpecBuilderImpl builder(FileSpec fileSpec) {
		return new FileMappingsSpecBuilderImpl(fileSpec);
	}

	@Override
	public FileMappingsSpecBuilderImpl bareFile() {
		return mappingPath(null);
	}

	@Override
	public FileMappingsSpecBuilderImpl mappingPath(@Nullable String mappingPath) {
		this.mappingPath = mappingPath;
		return this;
	}

	@Override
	public FileMappingsSpecBuilderImpl namespaces(String sourceNamespace, String targetNamespace) {
		fallbackSourceNamespace = Objects.requireNonNull(sourceNamespace, "fallback source namespace cannot be null");
		fallbackTargetNamespace = Objects.requireNonNull(targetNamespace, "fallback target namespace cannot be null");
		return this;
	}

	@Override
	public FileMappingsSpecBuilderImpl enigmaMappings() {
		mappingFormat = MappingFormat.ENIGMA;
		return this;
	}

	@Override
	public FileMappingsSpecBuilderImpl sourceNamespace(MappingsNamespace namespace) {
		sourceNamespace = Objects.requireNonNull(namespace, "source namespace cannot be null");
		return this;
	}

	public FileMappingsSpec build() {
		return new FileMappingsSpec(fileSpec, mappingPath, fallbackSourceNamespace, fallbackTargetNamespace, mappingFormat, sourceNamespace);
	}
}
