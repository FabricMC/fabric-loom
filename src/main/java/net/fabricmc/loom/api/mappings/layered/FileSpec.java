/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021 FabricMC
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

package net.fabricmc.loom.api.mappings.layered;

import java.io.File;
import java.util.Objects;

import org.gradle.api.file.RegularFileProperty;
import org.jetbrains.annotations.ApiStatus;

import net.fabricmc.loom.configuration.providers.mappings.DependencyFileSpecImpl;
import net.fabricmc.loom.configuration.providers.mappings.FileSpecImpl;

/**
 * FileSpec should be used in MappingsSpec's that take an input file. The input file can either be a local file or a gradle dep.
 */
@ApiStatus.Experimental
public interface FileSpec {
	static FileSpec create(Object o) {
		Objects.requireNonNull(o, "Object cannot be null");

		if (o instanceof String s) {
			if (s.startsWith("file://")) {
				return createFromFile(new File(s.substring("file://".length())));
			}

			return createFromDependency(s);
		} else if (o instanceof File f) {
			return createFromFile(f);
		} else if (o instanceof RegularFileProperty rfp) {
			return createFromFile(rfp);
		}

		throw new UnsupportedOperationException("Cannot create FileSpec from object of type:" + o.getClass().getCanonicalName());
	}

	static FileSpec createFromDependency(String dependencyNotation) {
		return new DependencyFileSpecImpl(dependencyNotation);
	}

	static FileSpec createFromFile(File file) {
		return new FileSpecImpl(file);
	}

	// Note resolved instantly, this is not lazy
	static FileSpec createFromFile(RegularFileProperty regularFileProperty) {
		return createFromFile(regularFileProperty.getAsFile().get());
	}

	File get(MappingContext context);
}
