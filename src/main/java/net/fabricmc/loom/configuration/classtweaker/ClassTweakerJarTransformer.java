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

package net.fabricmc.loom.configuration.classtweaker;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BiConsumer;

import org.jetbrains.annotations.VisibleForTesting;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

import net.fabricmc.classtweaker.api.ClassTweaker;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.FileSystemUtil;

public record ClassTweakerJarTransformer(ClassTweaker classTweaker) {
	public void transform(Path jar) throws IOException {
		try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(jar, false)) {
			transform(fs);
		}
	}

	@VisibleForTesting
	public void transform(FileSystemUtil.Delegate fs) throws IOException {
		for (String target : classTweaker.getTargets()) {
			transformClass(fs, target);
		}
	}

	private void transformClass(FileSystemUtil.Delegate fs, String name) throws IOException {
		final Path path = fs.getPath(nameToPath(name));
		byte[] inputBytes = Files.readAllBytes(path);

		final ClassReader reader = new ClassReader(inputBytes);
		final ClassWriter writer = new ClassWriter(0);

		final ClassVisitor classVisitor = classTweaker.createClassVisitor(Constants.ASM_VERSION, writer, consumeGeneratedClass(fs));
		reader.accept(classVisitor, 0);

		byte[] outputBytes = writer.toByteArray();
		Files.write(path, outputBytes);
	}

	private BiConsumer<String, byte[]> consumeGeneratedClass(FileSystemUtil.Delegate fs) {
		return (name, bytes) -> {
			final Path path = fs.getPath(nameToPath(name));

			try {
				Files.write(path, bytes);
			} catch (IOException e) {
				throw new UncheckedIOException("Failed to write generated class: %s".formatted(name), e);
			}
		};
	}

	private static String nameToPath(String name) {
		return name.replaceAll("\\.", "/") + ".class";
	}
}
