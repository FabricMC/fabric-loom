/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021-2026 FabricMC
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

package net.fabricmc.loom.decompilers.cfr;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.entities.AccessFlag;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.ClassFileField;
import org.benf.cfr.reader.entities.Field;
import org.benf.cfr.reader.mapping.NullMapping;
import org.benf.cfr.reader.util.output.DelegatingDumper;
import org.benf.cfr.reader.util.output.Dumper;

import net.fabricmc.loom.api.decompilers.JavadocStyle;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

public class CFRObfuscationMapping extends NullMapping {
	private final MappingTree mappingTree;
	private final JavadocStyle javadocStyle;

	public CFRObfuscationMapping(Path mappings, String runtimeNamespace, JavadocStyle javadocStyle) {
		mappingTree = readMappings(mappings, runtimeNamespace);
		this.javadocStyle = javadocStyle;
	}

	@Override
	public Dumper wrap(Dumper d) {
		return new JavadocProvidingDumper(d);
	}

	private static MappingTree readMappings(Path input, String runtimeNamespace) {
		try (BufferedReader reader = Files.newBufferedReader(input)) {
			MemoryMappingTree mappingTree = new MemoryMappingTree();
			MappingSourceNsSwitch nsSwitch = new MappingSourceNsSwitch(mappingTree, runtimeNamespace);
			MappingReader.read(reader, nsSwitch);

			return mappingTree;
		} catch (IOException e) {
			throw new RuntimeException("Failed to read mappings", e);
		}
	}

	private class JavadocProvidingDumper extends DelegatingDumper {
		private final DocPrinter docPrinter = DocPrinter.of(this, javadocStyle);

		JavadocProvidingDumper(Dumper delegate) {
			super(delegate);
		}

		@Override
		public Dumper dumpClassDoc(JavaTypeInstance owner) {
			MappingTree.ClassMapping mapping = getClassMapping(owner);

			if (mapping == null) {
				return this;
			}

			List<String> recordComponentDocs = new ArrayList<>();

			if (isRecord(owner)) {
				ClassFile classFile = ((JavaRefTypeInstance) owner).getClassFile();

				for (ClassFileField field : classFile.getFields()) {
					if (field.getField().testAccessFlag(AccessFlag.ACC_STATIC)) {
						continue;
					}

					MappingTree.FieldMapping fieldMapping = mapping.getField(field.getFieldName(), field.getField().getDescriptor());

					if (fieldMapping == null) {
						continue;
					}

					String comment = fieldMapping.getComment();

					if (comment != null) {
						recordComponentDocs.add(String.format("@param %s %s", fieldMapping.getSrcName(), comment));
					}
				}
			}

			String comment = mapping.getComment();

			if (comment != null || !recordComponentDocs.isEmpty()) {
				docPrinter.printHeader();

				if (comment != null) {
					for (String line : comment.split("\\R")) {
						docPrinter.printLine(line);
					}

					if (!recordComponentDocs.isEmpty()) {
						docPrinter.printEmptyLine();
					}
				}

				for (String componentDoc : recordComponentDocs) {
					docPrinter.printLine(componentDoc);
				}

				docPrinter.printFooter();
			}

			return this;
		}

		@Override
		public Dumper dumpMethodDoc(MethodPrototype method) {
			MappingTree.ClassMapping classMapping = getClassMapping(method.getOwner());

			if (classMapping == null) {
				return this;
			}

			List<String> lines = new ArrayList<>();
			MappingTree.MethodMapping mapping = classMapping.getMethod(method.getName(), method.getOriginalDescriptor());

			if (mapping != null) {
				String comment = mapping.getComment();

				if (comment != null) {
					lines.addAll(Arrays.asList(comment.split("\\R")));
				}

				final Collection<? extends MappingTree.MethodArgMapping> methodArgs = mapping.getArgs();
				final List<String> params = new ArrayList<>();

				for (MappingTree.MethodArgMapping arg : methodArgs) {
					String argComment = arg.getComment();

					if (argComment != null) {
						params.addAll(Arrays.asList(("@param " + arg.getSrcName() + " " + argComment).split("\\R")));
					}
				}

				// Add a blank line between params and the comment.
				if (!lines.isEmpty() && !params.isEmpty()) {
					lines.add("");
				}

				lines.addAll(params);
			}

			if (!lines.isEmpty()) {
				docPrinter.printComment(lines);
			}

			return this;
		}

		@Override
		public Dumper dumpFieldDoc(Field field, JavaTypeInstance owner) {
			// None static fields in records are handled in the class javadoc.
			if (isRecord(owner) && !isStatic(field)) {
				return this;
			}

			MappingTree.ClassMapping classMapping = getClassMapping(owner);

			if (classMapping == null) {
				return this;
			}

			MappingTree.FieldMapping fieldMapping = classMapping.getField(field.getFieldName(), field.getDescriptor());

			if (fieldMapping != null) {
				docPrinter.printComment(fieldMapping.getComment());
			}

			return this;
		}

		private MappingTree.ClassMapping getClassMapping(JavaTypeInstance type) {
			String qualifiedName = type.getRawName().replace('.', '/');
			return mappingTree.getClass(qualifiedName);
		}

		private boolean isRecord(JavaTypeInstance javaTypeInstance) {
			if (javaTypeInstance instanceof JavaRefTypeInstance) {
				ClassFile classFile = ((JavaRefTypeInstance) javaTypeInstance).getClassFile();
				return classFile.getClassSignature().getSuperClass().getRawName().equals("java.lang.Record");
			}

			return false;
		}

		private boolean isStatic(Field field) {
			return field.testAccessFlag(AccessFlag.ACC_STATIC);
		}
	}
}
