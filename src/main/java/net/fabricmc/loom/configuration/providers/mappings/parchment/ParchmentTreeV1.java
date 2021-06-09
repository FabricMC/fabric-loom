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

package net.fabricmc.loom.configuration.providers.mappings.parchment;

import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.mappingio.MappedElementKind;
import net.fabricmc.mappingio.MappingVisitor;

public record ParchmentTreeV1(
		String version,
		@Nullable
		List<Class> classes,
		@Nullable
		List<Package> packages
) {
	public void visit(MappingVisitor visitor, String srcNamespace) {
		while (true) {
			if (visitor.visitHeader()) {
				visitor.visitNamespaces(srcNamespace, Collections.emptyList());
			}

			if (visitor.visitContent()) {
				if (classes() != null && visitor.visitElementContent(MappedElementKind.CLASS)) {
					for (Class c : classes()) {
						c.visit(visitor);
					}
				}
			}

			if (visitor.visitEnd()) {
				break;
			}

			visitor.reset();
		}
	}

	public record Class(
			String name,
			@Nullable
			List<Field> fields,
			@Nullable
			List<Method> methods,
			@Nullable
			List<String> javadoc
	) {
		public void visit(MappingVisitor visitor) {
			if (visitor.visitClass(name())) {
				if (fields() != null && visitor.visitElementContent(MappedElementKind.FIELD)) {
					for (Field field : fields()) {
						field.visit(visitor);
					}
				}

				if (methods() != null && visitor.visitElementContent(MappedElementKind.METHOD)) {
					for (Method method : methods()) {
						method.visit(visitor);
					}
				}

				if (javadoc() != null && visitor.visitElementContent(MappedElementKind.CLASS)) {
					visitor.visitComment(MappedElementKind.CLASS, String.join("\n", javadoc()));
				}
			}
		}
	}

	public record Field(
			String name,
			String descriptor,
			@Nullable
			List<String> javadoc
	) {
		public void visit(MappingVisitor visitor) {
			if (visitor.visitField(name, descriptor)) {
				if (javadoc() != null && visitor.visitElementContent(MappedElementKind.FIELD)) {
					visitor.visitComment(MappedElementKind.FIELD, String.join("\n", javadoc()));
				}
			}
		}
	}

	public record Method(
			String name,
			String descriptor,
			@Nullable
			List<Parameter> parameters,
			@Nullable
			List<String> javadoc
	) {
		public void visit(MappingVisitor visitor) {
			if (visitor.visitMethod(name, descriptor)) {
				if (parameters() != null && visitor.visitElementContent(MappedElementKind.METHOD_ARG)) {
					for (Parameter parameter : parameters()) {
						parameter.visit(visitor);
					}
				}

				if (javadoc() != null && visitor.visitElementContent(MappedElementKind.METHOD)) {
					visitor.visitComment(MappedElementKind.METHOD, String.join("\n", javadoc()));
				}
			}
		}
	}

	public record Parameter(
			int index,
			String name,
			@Nullable
			String javadoc
	) {
		public void visit(MappingVisitor visitor) {
			// TODO is this the lvt index or what?
			visitor.visitMethodArg(index, index, name);

			if (javadoc() != null) {
				visitor.visitComment(MappedElementKind.METHOD_ARG, javadoc);
			}
		}
	}

	public record Package(
			String name,
			List<String> javadoc
	) { }
}
