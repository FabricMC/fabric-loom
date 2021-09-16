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

package net.fabricmc.loom.decompilers.fernflower;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.StructField;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.struct.StructRecordComponent;
import org.objectweb.asm.Opcodes;

import net.fabricmc.fernflower.api.IFabricJavadocProvider;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

public class TinyJavadocProvider implements IFabricJavadocProvider {
	private final MappingTree mappingTree;

	public TinyJavadocProvider(File tinyFile) {
		mappingTree = readMappings(tinyFile);
	}

	@Override
	public String getClassDoc(StructClass structClass) {
		MappingTree.ClassMapping classMapping = mappingTree.getClass(structClass.qualifiedName);

		if (classMapping == null) {
			return null;
		}

		if (!isRecord(structClass)) {
			return classMapping.getComment();
		}

		/**
		 * Handle the record component docs here.
		 *
		 * Record components are mapped via the field name, thus take the docs from the fields and display them on then class.
		 */
		List<String> parts = new ArrayList<>();

		if (classMapping.getComment() != null) {
			parts.add(classMapping.getComment());
		}

		boolean addedParam = false;

		for (StructRecordComponent component : structClass.getRecordComponents()) {
			// The component will always match the field name and descriptor
			MappingTree.FieldMapping fieldMapping = classMapping.getField(component.getName(), component.getDescriptor());

			if (fieldMapping == null) {
				continue;
			}

			String comment = fieldMapping.getComment();

			if (comment != null) {
				if (!addedParam && classMapping.getComment() != null) {
					//Add a blank line before components when the class has a comment
					parts.add("");
					addedParam = true;
				}

				parts.add(String.format("@param %s %s", fieldMapping.getName(MappingsNamespace.NAMED.toString()), comment));
			}
		}

		if (parts.isEmpty()) {
			return null;
		}

		return String.join("\n", parts);
	}

	@Override
	public String getFieldDoc(StructClass structClass, StructField structField) {
		// None static fields in records are handled in the class javadoc.
		if (isRecord(structClass) && !isStatic(structField)) {
			return null;
		}

		MappingTree.ClassMapping classMapping = mappingTree.getClass(structClass.qualifiedName);

		if (classMapping == null) {
			return null;
		}

		MappingTree.FieldMapping fieldMapping = classMapping.getField(structField.getName(), structField.getDescriptor());

		return fieldMapping != null ? fieldMapping.getComment() : null;
	}

	@Override
	public String getMethodDoc(StructClass structClass, StructMethod structMethod) {
		MappingTree.ClassMapping classMapping = mappingTree.getClass(structClass.qualifiedName);

		if (classMapping == null) {
			return null;
		}

		MappingTree.MethodMapping methodMapping = classMapping.getMethod(structMethod.getName(), structMethod.getDescriptor());

		if (methodMapping != null) {
			List<String> parts = new ArrayList<>();

			if (methodMapping.getComment() != null) {
				parts.add(methodMapping.getComment());
			}

			boolean addedParam = false;

			for (MappingTree.MethodArgMapping argMapping : methodMapping.getArgs()) {
				String comment = argMapping.getComment();

				if (comment != null) {
					if (!addedParam && methodMapping.getComment() != null) {
						//Add a blank line before params when the method has a comment
						parts.add("");
						addedParam = true;
					}

					parts.add(String.format("@param %s %s", methodMapping.getName(MappingsNamespace.NAMED.toString()), comment));
				}
			}

			if (parts.isEmpty()) {
				return null;
			}

			return String.join("\n", parts);
		}

		return null;
	}

	private static MappingTree readMappings(File input) {
		try (BufferedReader reader = Files.newBufferedReader(input.toPath())) {
			MemoryMappingTree mappingTree = new MemoryMappingTree();
			MappingSourceNsSwitch nsSwitch = new MappingSourceNsSwitch(mappingTree, MappingsNamespace.NAMED.toString());
			MappingReader.read(reader, nsSwitch);

			return mappingTree;
		} catch (IOException e) {
			throw new RuntimeException("Failed to read mappings", e);
		}
	}

	public static boolean isRecord(StructClass structClass) {
		return (structClass.getAccessFlags() & Opcodes.ACC_RECORD) != 0;
	}

	public static boolean isStatic(StructField structField) {
		return (structField.getAccessFlags() & Opcodes.ACC_STATIC) != 0;
	}
}
