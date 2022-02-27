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

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Objects;

import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Provider;
import org.jetbrains.annotations.ApiStatus;

import net.fabricmc.loom.api.mappings.layered.MappingContext;
import net.fabricmc.loom.configuration.providers.mappings.utils.DependencyFileSpec;
import net.fabricmc.loom.configuration.providers.mappings.utils.URLFileSpec;
import net.fabricmc.loom.configuration.providers.mappings.utils.LocalFileSpec;
import net.fabricmc.loom.configuration.providers.mappings.utils.MavenFileSpec;

/**
 * FileSpec should be used in MappingsSpec's that take an input file. The input file can either be a local file or a gradle dep.
 */
@ApiStatus.Experimental
public interface FileSpec {
	/**
	 * Creates a file spec.
	 *
	 * <p>The parameter will be evaluated like this:
	 * <ul>
	 * <li>{@link File}, {@link Path} and {@link FileSystemLocation} will be resolved as local files</li>
	 * <li>{@link Provider} (including {@link org.gradle.api.provider.Property}) will recursively be resolved as its current value</li>
	 * <li>{@link CharSequence} (including {@link String} and {@link groovy.lang.GString}) will be resolved as Maven dependencies</li>
	 * <li>{@link Dependency} will be resolved as any dependency</li>
	 * <li>{@code FileSpec} will just return the spec itself</li>
	 * </ul>
	 *
	 * @param o the file notation
	 * @return the created file spec
	 */
	static FileSpec create(Object o) {
		Objects.requireNonNull(o, "Object cannot be null");

		if (o instanceof CharSequence s) {
			if (s.toString().startsWith("https://") || s.toString().startsWith("http://")) {
				try {
					return create(new URL(s.toString()));
				} catch (MalformedURLException e) {
					throw new RuntimeException("Failed to convert string to URL", e);
				}
			}

			return createFromMavenDependency(s.toString());
		} else if (o instanceof Dependency d) {
			return createFromDependency(d);
		} else if (o instanceof Provider<?> p) {
			return create(p.get());
		} else if (o instanceof File f) {
			return createFromFile(f);
		} else if (o instanceof Path p) {
			return createFromFile(p);
		} else if (o instanceof FileSystemLocation l) {
			return createFromFile(l);
		} else if (o instanceof URL url) {
			return createFromUrl(url);
		} else if (o instanceof FileSpec s) {
			return s;
		}

		throw new UnsupportedOperationException("Cannot create FileSpec from object of type:" + o.getClass().getCanonicalName());
	}

	static FileSpec createFromMavenDependency(String dependencyNotation) {
		return new MavenFileSpec(dependencyNotation);
	}

	static FileSpec createFromDependency(Dependency dependency) {
		return new DependencyFileSpec(dependency);
	}

	static FileSpec createFromFile(File file) {
		return new LocalFileSpec(file);
	}

	static FileSpec createFromFile(FileSystemLocation location) {
		return createFromFile(location.getAsFile());
	}

	static FileSpec createFromFile(Path path) {
		return createFromFile(path.toFile());
	}

	static FileSpec createFromUrl(URL url) {
		return new URLFileSpec(url);
	}

	// Note resolved instantly, this is not lazy
	static FileSpec createFromFile(RegularFileProperty regularFileProperty) {
		return createFromFile(regularFileProperty.get());
	}

	Path get(MappingContext context);
}
