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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
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

import net.fabricmc.mappings.EntryTriple;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.Checksum;

public class AccessEscalatorJarProcessor implements JarProcessor {
	private AccessEscalator accessEscalator = new AccessEscalator();
	private Project project;
	private byte[] inputHash;

	@Override
	public void setup(Project project) {
		this.project = project;
		LoomGradleExtension loomGradleExtension = project.getExtensions().getByType(LoomGradleExtension.class);

		inputHash = Checksum.sha256(loomGradleExtension.accessEscalator);

		try (BufferedReader reader = new BufferedReader(new FileReader(loomGradleExtension.accessEscalator))) {
			accessEscalator.read(reader);
		} catch (IOException e) {
			throw new RuntimeException("Failed to read project access escalator file");
		}

		//Remap accessEscalator if its not named, allows for AE's to be written in intermediary
		if (!accessEscalator.namespace.equals("named")) {
			try {
				AccessEscalatorRemapper remapper = new AccessEscalatorRemapper(accessEscalator, loomGradleExtension.getMappingsProvider().getMappings(), "named");
				accessEscalator = remapper.remap();
			} catch (IOException e) {
				throw new RuntimeException("Failed to remap access escalator", e);
			}
		}
	}

	@Override
	public void process(File file) {
		project.getLogger().lifecycle("Processing file: " + file.getName());
		ZipUtil.transformEntries(file, getTransformers(accessEscalator.getTargets()));
		ZipUtil.addEntry(file, "ae.sha256", inputHash);
	}

	private ZipEntryTransformerEntry[] getTransformers(Set<String> classes) {
		return classes.stream()
				.map(string -> new ZipEntryTransformerEntry(string + ".class", getTransformer(string)))
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

	//Called when remapping the mod
	public void addAccessEscalatorFile(Path modJarPath) throws IOException {
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		AccessEscalatorRemapper remapper = new AccessEscalatorRemapper(accessEscalator, extension.getMappingsProvider().getMappings(), "intermediary");
		AccessEscalator remapped = remapper.remap();

		StringWriter writer = new StringWriter();
		remapped.write(writer);
		byte[] bytes = writer.toString().getBytes();
		writer.close();

		ZipUtil.addEntry(modJarPath.toFile(), "META-INF/access.ea", bytes);
	}

	@Override
	public boolean isInvalid(File file) {
		byte[] hash = ZipUtil.unpackEntry(file, "ae.sha256");

		if (hash == null) {
			return true;
		}

		return !Arrays.equals(inputHash, hash); //TODO how do we know if the current jar as the correct access applied? save the hash of the input?
	}

	private class AccessTransformer extends ClassVisitor {
		private String className;

		private AccessTransformer(ClassVisitor classVisitor) {
			super(Opcodes.ASM7, classVisitor);
		}

		@Override
		public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
			className = name;
			super.visit(
					version,
					accessEscalator.getClassAccess(name).apply(access),
					name,
					signature,
					superName,
					interfaces
			);
		}

		@Override
		public void visitInnerClass(String name, String outerName, String innerName, int access) {
			super.visitInnerClass(
					name,
					outerName,
					innerName,
					accessEscalator.getClassAccess(name).apply(access)
			);
		}

		@Override
		public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
			return super.visitField(
					accessEscalator.getFieldAccess(new EntryTriple(className, name, descriptor)).apply(access),
					name,
					descriptor,
					signature,
					value
			);
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
			return super.visitMethod(
					accessEscalator.getMethodAccess(new EntryTriple(className, name, descriptor)).apply(access),
					name,
					descriptor,
					signature,
					exceptions
			);
		}
	}
}
