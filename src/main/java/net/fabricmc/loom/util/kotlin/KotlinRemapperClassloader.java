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

package net.fabricmc.loom.util.kotlin;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import kotlinx.metadata.jvm.KotlinClassHeader;

import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.kotlin.remapping.KotlinMetadataTinyRemapperExtensionImpl;

public class KotlinRemapperClassloader extends URLClassLoader {
	private static final List<String> PARENT_PACKAGES = List.of(
			"net.fabricmc.tinyremapper",
			"net.fabricmc.loom.util.kotlin",
			"org.objectweb.asm",
			"org.slf4j"
	);

	private KotlinRemapperClassloader(URL[] urls) {
		super(urls, null);
	}

	@Override
	protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		if (PARENT_PACKAGES.stream().anyMatch(name::startsWith)) {
			return LoomGradlePlugin.class.getClassLoader().loadClass(name);
		}

		return super.loadClass(name, resolve);
	}

	public static KotlinRemapperClassloader create(KotlinClasspath classpathProvider) {
		// Include the libraries that may not be on the compile classpath
		final Stream<URL> loomUrls = getClassUrls(
				KotlinMetadataTinyRemapperExtensionImpl.class, // Loom
				KotlinClassHeader.class // Kotlin metadata api
		);

		final Stream<URL> kotlinClasspath = classpathProvider.classpath().stream();

		final URL[] urls = Stream.concat(loomUrls, kotlinClasspath).toArray(URL[]::new);

		return new KotlinRemapperClassloader(urls);
	}

	private static Stream<URL> getClassUrls(Class<?>... classes) {
		return Arrays.stream(classes).map(klass -> klass.getProtectionDomain().getCodeSource().getLocation());
	}

	public KotlinMetadataTinyRemapperExtension getTinyRemapperExtension() {
		try {
			Class<?> klass = this.loadClass(KotlinMetadataTinyRemapperExtensionImpl.class.getCanonicalName());
			return (KotlinMetadataTinyRemapperExtension) klass.getField("INSTANCE").get(null);
		} catch (ClassNotFoundException | IllegalAccessException | NoSuchFieldException e) {
			throw new RuntimeException("Failed to create instance", e);
		}
	}
}
