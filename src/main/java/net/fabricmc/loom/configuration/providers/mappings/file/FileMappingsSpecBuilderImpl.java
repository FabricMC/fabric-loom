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
import java.util.Optional;

import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.api.mappings.layered.spec.FileMappingsSpecBuilder;
import net.fabricmc.loom.api.mappings.layered.spec.FileSpec;
import net.fabricmc.loom.configuration.providers.mappings.utils.MavenFileSpec;

public class FileMappingsSpecBuilderImpl implements FileMappingsSpecBuilder {
	/**
	 * The mapping path of regular mapping dependencies.
	 */
	private static final String DEFAULT_MAPPING_PATH = "mappings/mappings.tiny";

	private final FileSpec fileSpec;
	private String mappingPath = DEFAULT_MAPPING_PATH;
	private Optional<String> fallbackSourceNamespace = Optional.empty();
	private String fallbackTargetNamespace = MappingsNamespace.NAMED.toString();
	private boolean enigma = false;
	private boolean unpick = false;
	private boolean annotations = false;
	private Optional<String> mergeNamespace = Optional.empty();
	private Optional<String> fallbackUnpickConstants = Optional.empty();

	private FileMappingsSpecBuilderImpl(FileSpec fileSpec) {
		this.fileSpec = fileSpec;
	}

	public static FileMappingsSpecBuilderImpl builder(FileSpec fileSpec) {
		return new FileMappingsSpecBuilderImpl(fileSpec);
	}

	@Override
	public FileMappingsSpecBuilderImpl mappingPath(String mappingPath) {
		this.mappingPath = Objects.requireNonNull(mappingPath, "mapping path cannot be null");
		return this;
	}

	@Override
	public FileMappingsSpecBuilderImpl fallbackNamespaces(String sourceNamespace, String targetNamespace) {
		fallbackSourceNamespace = Optional.of(Objects.requireNonNull(sourceNamespace, "fallback source namespace cannot be null"));
		fallbackTargetNamespace = Objects.requireNonNull(targetNamespace, "fallback target namespace cannot be null");
		return this;
	}

	@Override
	public FileMappingsSpecBuilderImpl enigmaMappings() {
		enigma = true;
		return this;
	}

	@Override
	public FileMappingsSpecBuilder containsAnnotations() {
		annotations = true;
		return this;
	}

	@Override
	public FileMappingsSpecBuilderImpl containsUnpick() {
		unpick = true;

		if (fileSpec instanceof MavenFileSpec mavenFileSpec) {
			String dependencyNotation = mavenFileSpec.dependencyNotation();
			String[] notationParts = dependencyNotation.split(":");

			if (notationParts.length == 4) {
				dependencyNotation = dependencyNotation.substring(0, dependencyNotation.lastIndexOf(':'));
			}

			fallbackUnpickConstants = Optional.of(dependencyNotation + ":constants");
		}

		return this;
	}

	@Override
	public FileMappingsSpecBuilderImpl mergeNamespace(MappingsNamespace namespace) {
		mergeNamespace = Optional.of(Objects.requireNonNull(namespace, "merge namespace cannot be null").toString());
		return this;
	}

	@Override
	public FileMappingsSpecBuilderImpl mergeNamespace(String namespace) {
		Objects.requireNonNull(namespace, "merge namespace cannot be null");

		if (MappingsNamespace.of(namespace) == null) {
			throw new IllegalArgumentException("Namespace '" + namespace + "' is unsupported! It must be either 'official', 'intermediary' or 'named'.");
		}

		mergeNamespace = Optional.of(namespace);
		return this;
	}

	public FileMappingsSpec build() {
		return new FileMappingsSpec(fileSpec, mappingPath, fallbackSourceNamespace, fallbackTargetNamespace, enigma, unpick, annotations, mergeNamespace, fallbackUnpickConstants);
	}
}
