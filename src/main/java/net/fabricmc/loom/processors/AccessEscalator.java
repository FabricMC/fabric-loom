/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016, 2017, 2018 FabricMC
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

package net.fabricmc.loom.processors;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;

import org.gradle.api.Project;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.zeroturnaround.zip.ZipUtil;
import org.zeroturnaround.zip.transform.ByteArrayZipEntryTransformer;
import org.zeroturnaround.zip.transform.ZipEntryTransformer;
import org.zeroturnaround.zip.transform.ZipEntryTransformerEntry;

import net.fabricmc.loom.LoomGradleExtension;

public class AccessEscalator implements JarProcessor {
	private File accessEscalatorFile;
	private Project project;

	@Override
	public void setup(Project project) {
		this.project = project;
		LoomGradleExtension loomGradleExtension = project.getExtensions().getByType(LoomGradleExtension.class);
		accessEscalatorFile = loomGradleExtension.accessEscalator;

		if (accessEscalatorFile == null || !accessEscalatorFile.exists()) {
			throw new RuntimeException("Failed to find accessEscalator file specified");
		}
	}

	@Override
	public void process(File file) {
		project.getLogger().lifecycle("Processing file: " + file.getName());

		//Example code used for testing
		List<String> classes = new ArrayList<>();
		ZipUtil.iterate(file, zipEntry -> {
			if (zipEntry.getName().endsWith(".class")) {
				classes.add(zipEntry.getName());
			}
		});

		ZipUtil.transformEntries(file, getTransformers(classes));
	}

	private ZipEntryTransformerEntry[] getTransformers(List<String> classes) {
		return classes.stream()
				.map(string -> new ZipEntryTransformerEntry(string, getTransformer(string)))
				.toArray(ZipEntryTransformerEntry[]::new);
	}

	private ZipEntryTransformer getTransformer(String className) {
		return new ByteArrayZipEntryTransformer() {
			@Override
			protected byte[] transform(ZipEntry zipEntry, byte[] input) {
				ClassReader reader = new ClassReader(input);
				ClassWriter writer = new ClassWriter(0);

				project.getLogger().lifecycle("Applying access escalator to " + className);

				reader.accept(new AccessTransformer(writer), 0);
				return writer.toByteArray();
			}
		};
	}

	@Override
	public boolean isInvalid(File file) {
		return true; //TODO how do we know if the current jar as the correct access applied? save the hash of the input
	}

	//Example to make everything public
	private static class AccessTransformer extends ClassVisitor {
		private AccessTransformer(ClassVisitor classVisitor) {
			super(Opcodes.ASM7, classVisitor);
		}

		private static int modAccess(int access) {
			if ((access & 0x7) != Opcodes.ACC_PRIVATE) {
				return (access & (~0x7)) | Opcodes.ACC_PUBLIC;
			} else {
				return access;
			}
		}

		@Override
		public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
			super.visit(version, modAccess(access), name, signature, superName, interfaces);
		}

		@Override
		public void visitInnerClass(String name, String outerName, String innerName, int access) {
			super.visitInnerClass(name, outerName, innerName, modAccess(access));
		}

		@Override
		public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
			return super.visitField(modAccess(access), name, descriptor, signature, value);
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
			return super.visitMethod(modAccess(access), name, descriptor, signature, exceptions);
		}
	}
}
