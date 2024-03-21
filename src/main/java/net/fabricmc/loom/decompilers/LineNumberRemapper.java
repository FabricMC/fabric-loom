/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2019-2021 FabricMC
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

package net.fabricmc.loom.decompilers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.util.AsyncZipProcessor;
import net.fabricmc.loom.util.Constants;

public record LineNumberRemapper(ClassLineNumbers lineNumbers) {
	private static final Logger LOGGER = LoggerFactory.getLogger(LineNumberRemapper.class);

	public void process(Path input, Path output) throws IOException {
		AsyncZipProcessor.processEntries(input, output, new AsyncZipProcessor() {
			private final Set<Path> createdParents = new HashSet<>();

			@Override
			public void processEntryAsync(Path file, Path dst) throws IOException {
				Path parent = dst.getParent();

				synchronized (createdParents) {
					if (parent != null && createdParents.add(parent)) {
						Files.createDirectories(parent);
					}
				}

				String fileName = file.getFileName().toString();

				if (fileName.endsWith(".class")) {
					String idx = fileName.substring(0, fileName.length() - 6);

					LOGGER.debug("Remapping line numbers for class: " + idx);

					int dollarPos = idx.indexOf('$'); //This makes the assumption that only Java classes are to be remapped.

					if (dollarPos >= 0) {
						idx = idx.substring(0, dollarPos);
					}

					if (lineNumbers.lineMap().containsKey(idx)) {
						try (InputStream is = Files.newInputStream(file)) {
							ClassReader reader = new ClassReader(is);
							ClassWriter writer = new ClassWriter(0);

							reader.accept(new LineNumberVisitor(Constants.ASM_VERSION, writer, lineNumbers.lineMap().get(idx)), 0);
							Files.write(dst, writer.toByteArray());
							return;
						}
					}
				}

				Files.copy(file, dst, StandardCopyOption.REPLACE_EXISTING);
			}
		});
	}

	private static class LineNumberVisitor extends ClassVisitor {
		private final ClassLineNumbers.Entry lineNumbers;

		LineNumberVisitor(int api, ClassVisitor classVisitor, ClassLineNumbers.Entry lineNumbers) {
			super(api, classVisitor);
			this.lineNumbers = lineNumbers;
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
			return new MethodVisitor(api, super.visitMethod(access, name, descriptor, signature, exceptions)) {
				@Override
				public void visitLineNumber(int line, Label start) {
					int tLine = line;

					if (tLine <= 0) {
						super.visitLineNumber(line, start);
					} else if (tLine >= lineNumbers.maxLine()) {
						super.visitLineNumber(lineNumbers.maxLineDest(), start);
					} else {
						Integer matchedLine = null;

						while (tLine <= lineNumbers.maxLine() && ((matchedLine = lineNumbers.lineMap().get(tLine)) == null)) {
							tLine++;
						}

						super.visitLineNumber(matchedLine != null ? matchedLine : lineNumbers.maxLineDest(), start);
					}
				}
			};
		}
	}
}
