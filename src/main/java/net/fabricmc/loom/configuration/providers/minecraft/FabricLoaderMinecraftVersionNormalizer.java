/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2026 FabricMC
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

package net.fabricmc.loom.configuration.providers.minecraft;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.WrongMethodTypeException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Optional;
import java.util.Set;

import org.gradle.api.Project;

import net.fabricmc.loom.util.Constants;

final class FabricLoaderMinecraftVersionNormalizer implements MinecraftVersionNormalizer {
	private final URLClassLoader classLoader;
	private final MethodHandle getRelease;
	private final MethodHandle normalizeVersion;
	private final MethodHandle parseSemanticVersion;

	static FabricLoaderMinecraftVersionNormalizer create(Project project) {
		Set<File> files = project.getConfigurations()
				.getByName(Constants.Configurations.MINECRAFT_VERSION_NORMALIZER)
				.resolve();
		URL[] urls = files.stream()
				.map(FabricLoaderMinecraftVersionNormalizer::toUrl)
				.toArray(URL[]::new);

		return new FabricLoaderMinecraftVersionNormalizer(urls);
	}

	private FabricLoaderMinecraftVersionNormalizer(URL[] urls) {
		this.classLoader = new FabricLoaderClassLoader(urls, FabricLoaderMinecraftVersionNormalizer.class.getClassLoader());

		try {
			MethodHandles.Lookup publicLookup = MethodHandles.publicLookup();
			Class<?> lookup = this.classLoader.loadClass("net.fabricmc.loader.impl.game.minecraft.McVersionLookup");
			this.getRelease = publicLookup.findStatic(lookup, "getRelease", MethodType.methodType(String.class, String.class));
			this.normalizeVersion = publicLookup.findStatic(lookup, "normalizeVersion", MethodType.methodType(String.class, String.class, String.class));

			Class<?> semanticVersion = this.classLoader.loadClass("net.fabricmc.loader.api.SemanticVersion");
			this.parseSemanticVersion = publicLookup.findStatic(semanticVersion, "parse", MethodType.methodType(semanticVersion, String.class));
		} catch (ReflectiveOperationException e) {
			closeQuietly(this.classLoader);
			throw new RuntimeException("Failed to load Fabric Loader Minecraft version normalizer", e);
		}
	}

	@Override
	public Optional<String> normalize(String version) {
		try {
			String release = (String) this.getRelease.invoke(version);
			String normalized = (String) this.normalizeVersion.invoke(version, release);
			this.parseSemanticVersion.invoke(normalized);
			return Optional.of(normalized);
		} catch (WrongMethodTypeException | ClassCastException e) {
			throw new RuntimeException("Failed to normalize Minecraft version: " + version, e);
		} catch (LinkageError e) {
			throw e;
		} catch (Throwable e) {
			return Optional.empty();
		}
	}

	@Override
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public int compare(String a, String b) {
		try {
			final Comparable parsedA = (Comparable) this.parseSemanticVersion.invoke(a);
			final Object parsedB = this.parseSemanticVersion.invoke(b);
			return parsedA.compareTo(parsedB);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to compare Minecraft versions: %s and %s".formatted(a, b), e);
		}
	}

	@Override
	public void close() throws IOException {
		this.classLoader.close();
	}

	private static URL toUrl(File file) {
		try {
			return file.toURI().toURL();
		} catch (IOException e) {
			throw new RuntimeException("Failed to create URL for: " + file, e);
		}
	}

	private static void closeQuietly(Closeable closeable) {
		try {
			closeable.close();
		} catch (IOException e) {
			// ignored
		}
	}

	private static final class FabricLoaderClassLoader extends URLClassLoader {
		private FabricLoaderClassLoader(URL[] urls, ClassLoader parent) {
			super(urls, parent);
		}

		@Override
		protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
			if (!name.startsWith("net.fabricmc.loader.")) {
				return super.loadClass(name, resolve);
			}

			synchronized (getClassLoadingLock(name)) {
				Class<?> loadedClass = findLoadedClass(name);

				if (loadedClass == null) {
					try {
						// The normalizer version is intentionally configurable. Prefer the configured
						// Loader classes over any Loader already present on Loom's/test classpath.
						loadedClass = findClass(name);
					} catch (ClassNotFoundException e) {
						loadedClass = super.loadClass(name, false);
					}
				}

				if (resolve) {
					resolveClass(loadedClass);
				}

				return loadedClass;
			}
		}
	}
}
