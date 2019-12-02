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

package net.fabricmc.loom.task.fernflower;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.StructField;
import org.jetbrains.java.decompiler.struct.StructMethod;

import net.fabricmc.fernflower.api.IFabricJavadocProvider;
import net.fabricmc.mapping.tree.ClassDef;
import net.fabricmc.mapping.tree.FieldDef;
import net.fabricmc.mapping.tree.MethodDef;
import net.fabricmc.mapping.tree.TinyMappingFactory;
import net.fabricmc.mapping.tree.TinyTree;
import net.fabricmc.mappings.EntryTriple;

public class TinyJavadocProvider implements IFabricJavadocProvider {
	private final Map<String, String> classComments = new HashMap<>();
	private final Map<EntryTriple, String> fieldComments = new HashMap<>();
	private final Map<EntryTriple, String> methodComments = new HashMap<>();

	public TinyJavadocProvider(File tinyFile) {
		final TinyTree mappings = readMappings(tinyFile);
		final String to = "named";

		for (ClassDef classDef : mappings.getClasses()) {
			final String className = classDef.getName(to);
			final String classComment = classDef.getComment();

			if (classComment != null) {
				classComments.put(className, classComment);
			}

			for (FieldDef fieldDef : classDef.getFields()) {
				final String fieldComment = fieldDef.getComment();

				if (fieldComment != null) {
					fieldComments.put(new EntryTriple(className, fieldDef.getName(to), fieldDef.getDescriptor(to)), fieldComment);
				}
			}

			for (MethodDef methodDef : classDef.getMethods()) {
				final String methodComment = methodDef.getComment();

				if (methodComment != null) {
					methodComments.put(new EntryTriple(className, methodDef.getName(to), methodDef.getDescriptor(to)), methodComment);
				}
			}
		}
	}

	@Override
	public String getClassDoc(StructClass structClass) {
		return classComments.getOrDefault(structClass.qualifiedName, null);
	}

	@Override
	public String getFieldDoc(StructClass structClass, StructField structField) {
		return fieldComments.getOrDefault(new EntryTriple(structClass.qualifiedName, structField.getName(), structField.getDescriptor()), null);
	}

	@Override
	public String getMethodDoc(StructClass structClass, StructMethod structMethod) {
		return methodComments.getOrDefault(new EntryTriple(structClass.qualifiedName, structMethod.getName(), structMethod.getDescriptor()), null);
	}

	private static TinyTree readMappings(File input) {
		try (BufferedReader reader = Files.newBufferedReader(input.toPath())) {
			return TinyMappingFactory.loadWithDetection(reader);
		} catch (IOException e) {
			throw new RuntimeException("Failed to read mappings", e);
		}
	}
}
