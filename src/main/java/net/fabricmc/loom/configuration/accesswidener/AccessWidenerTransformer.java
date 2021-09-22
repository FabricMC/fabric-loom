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

package net.fabricmc.loom.configuration.accesswidener;

import java.io.File;
import java.util.Set;
import java.util.zip.ZipEntry;

import org.gradle.api.logging.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.zeroturnaround.zip.ZipUtil;
import org.zeroturnaround.zip.transform.ByteArrayZipEntryTransformer;
import org.zeroturnaround.zip.transform.ZipEntryTransformer;
import org.zeroturnaround.zip.transform.ZipEntryTransformerEntry;

import net.fabricmc.accesswidener.AccessWidener;
import net.fabricmc.accesswidener.AccessWidenerClassVisitor;
import net.fabricmc.loom.util.Constants;

final class AccessWidenerTransformer {
	private final Logger logger;
	private final AccessWidener accessWidener;

	AccessWidenerTransformer(Logger logger, AccessWidener accessWidener) {
		this.logger = logger;
		this.accessWidener = accessWidener;
	}

	/**
	 * Apply the rules from an access-widener to the given jar or zip file.
	 */
	void apply(File jarFile) {
		logger.lifecycle("Processing file: " + jarFile.getName());
		ZipUtil.transformEntries(jarFile, getTransformers(accessWidener.getTargets()));
	}

	private ZipEntryTransformerEntry[] getTransformers(Set<String> classes) {
		return classes.stream()
				.map(string -> new ZipEntryTransformerEntry(string.replaceAll("\\.", "/") + ".class", getTransformer(string)))
				.toArray(ZipEntryTransformerEntry[]::new);
	}

	private ZipEntryTransformer getTransformer(String className) {
		return new ByteArrayZipEntryTransformer() {
			@Override
			protected byte[] transform(ZipEntry zipEntry, byte[] input) {
				ClassReader reader = new ClassReader(input);
				ClassWriter writer = new ClassWriter(0);
				ClassVisitor classVisitor = AccessWidenerClassVisitor.createClassVisitor(Constants.ASM_VERSION, writer, accessWidener);

				logger.info("Applying access widener to " + className);

				reader.accept(classVisitor, 0);
				return writer.toByteArray();
			}
		};
	}
}
