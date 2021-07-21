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

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.zeroturnaround.zip.ZipUtil;

public class JarProcessorManager {
	private final List<JarProcessor> jarProcessors;
	private String id;

	public JarProcessorManager(List<JarProcessor> jarProcessors) {
		this.jarProcessors = jarProcessors;
	}

	public void setupProcessors() {
		jarProcessors.forEach(JarProcessor::setup);

		this.id = jarProcessors.stream()
				.sorted(Comparator.comparing(jarProcessor -> jarProcessor.getClass().getCanonicalName()))
				.map(JarProcessor::getId)
				.collect(Collectors.joining(":"));
	}

	public boolean active() {
		return !jarProcessors.isEmpty();
	}

	public boolean isInvalid(File file) {
		Objects.requireNonNull(id);

		if (!file.exists()) {
			return true;
		}

		byte[] idBytes = ZipUtil.unpackEntry(file, "LoomJarProcessorId");

		if (idBytes == null) {
			return true;
		}

		String jarId = new String(idBytes, StandardCharsets.UTF_8);
		return !jarId.equals(id);
	}

	public void process(File file) {
		for (JarProcessor jarProcessor : jarProcessors) {
			jarProcessor.process(file);
		}
	}

	public <T extends JarProcessor> T getByType(Class<T> tClass) {
		//noinspection unchecked
		return (T) jarProcessors.stream().filter(jarProcessor -> jarProcessor.getClass().equals(tClass)).findFirst().orElse(null);
	}
}
