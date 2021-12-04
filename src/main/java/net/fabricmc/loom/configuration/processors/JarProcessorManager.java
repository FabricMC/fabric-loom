/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2020 FabricMC
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

package net.fabricmc.loom.configuration.processors;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

import com.google.common.base.Preconditions;
import com.google.common.hash.Hashing;
import com.google.common.io.CharSource;

import net.fabricmc.loom.util.ZipUtils;

public class JarProcessorManager {
	private static final String MANIFEST_PATH = "META-INF/MANIFEST.MF";
	private static final String JAR_PROCESSOR_HASH_ATTRIBUTE = "Loom-Jar-Processor-Hash";
	private final List<JarProcessor> jarProcessors;

	public JarProcessorManager(List<JarProcessor> jarProcessors) {
		this.jarProcessors = jarProcessors;
	}

	public void setupProcessors() {
		jarProcessors.forEach(JarProcessor::setup);
	}

	public boolean active() {
		return !jarProcessors.isEmpty();
	}

	public boolean isInvalid(File file) {
		if (!file.exists()) {
			return true;
		}

		String jarProcessorHash = getJarProcessorHash();

		try (JarFile jar = new JarFile(file)) {
			Manifest manifest = jar.getManifest();

			if (manifest == null) {
				return false;
			}

			Attributes attributes = manifest.getMainAttributes();

			if (!jarProcessorHash.equals(attributes.getValue(JAR_PROCESSOR_HASH_ATTRIBUTE))) {
				return true;
			}
		} catch (IOException e) {
			throw new UncheckedIOException("Could not check jar manifest of " + file, e);
		}

		return jarProcessors.stream().anyMatch(jarProcessor -> jarProcessor.isInvalid(file));
	}

	private String getJarProcessorHash() {
		String jarProcessorIds = jarProcessors.stream()
				.map(JarProcessor::getId)
				.sorted()
				.collect(Collectors.joining(";"));

		try {
			return CharSource.wrap(jarProcessorIds)
					.asByteSource(StandardCharsets.UTF_8)
					.hash(Hashing.sha256())
					.toString();
		} catch (IOException e) {
			throw new UncheckedIOException("Could not hash jar processor IDs", e);
		}
	}

	public void process(File file) {
		for (JarProcessor jarProcessor : jarProcessors) {
			jarProcessor.process(file);
		}

		try {
			int count = ZipUtils.transform(file.toPath(), Map.of(MANIFEST_PATH, bytes -> {
				Manifest manifest = new Manifest(new ByteArrayInputStream(bytes));
				manifest.getMainAttributes().putValue(JAR_PROCESSOR_HASH_ATTRIBUTE, getJarProcessorHash());
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				manifest.write(out);
				return out.toByteArray();
			}));

			Preconditions.checkState(count > 0, "Did not add data to jar manifest in " + file);
		} catch (IOException e) {
			throw new UncheckedIOException("Could not add data to jar manifest in " + file, e);
		}
	}

	public <T extends JarProcessor> T getByType(Class<T> tClass) {
		//noinspection unchecked
		return (T) jarProcessors.stream().filter(jarProcessor -> jarProcessor.getClass().equals(tClass)).findFirst().orElse(null);
	}
}
