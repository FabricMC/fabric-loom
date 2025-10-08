/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 FabricMC
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

package net.fabricmc.loom.configuration.providers.mappings.extras.annotations;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.TypeAnnotationNode;

import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.api.TrRemapper;

public record ClassAnnotationData(
		@SerializedName("remove")
		Set<String> annotationsToRemove,
		@SerializedName("add")
		List<AnnotationNode> annotationsToAdd,
		@SerializedName("type_remove")
		Set<TypeAnnotationKey> typeAnnotationsToRemove,
		@SerializedName("type_add")
		List<TypeAnnotationNode> typeAnnotationsToAdd,
		Map<String, GenericAnnotationData> fields,
		Map<String, MethodAnnotationData> methods
) implements BaseAnnotationData {
	public ClassAnnotationData {
		if (annotationsToRemove == null) {
			annotationsToRemove = new LinkedHashSet<>();
		}

		if (annotationsToAdd == null) {
			annotationsToAdd = new ArrayList<>();
		}

		if (typeAnnotationsToRemove == null) {
			typeAnnotationsToRemove = new LinkedHashSet<>();
		}

		if (typeAnnotationsToAdd == null) {
			typeAnnotationsToAdd = new ArrayList<>();
		}

		if (fields == null) {
			fields = new LinkedHashMap<>();
		}

		if (methods == null) {
			methods = new LinkedHashMap<>();
		}
	}

	public ClassAnnotationData() {
		this(new LinkedHashSet<>(), new ArrayList<>(), new LinkedHashSet<>(), new ArrayList<>(), new LinkedHashMap<>(), new LinkedHashMap<>());
	}

	public ClassAnnotationData(ClassAnnotationData other) {
		this(
				new LinkedHashSet<>(other.annotationsToRemove),
				AnnotationsData.copyAnnotations(other.annotationsToAdd),
				new LinkedHashSet<>(other.typeAnnotationsToRemove),
				AnnotationsData.copyTypeAnnotations(other.typeAnnotationsToAdd),
				AnnotationsData.copyMap(other.fields, GenericAnnotationData::new),
				AnnotationsData.copyMap(other.methods, MethodAnnotationData::new)
		);
	}

	ClassAnnotationData merge(ClassAnnotationData other) {
		Set<String> newAnnotationsToRemove = new LinkedHashSet<>(annotationsToRemove);
		newAnnotationsToRemove.addAll(other.annotationsToRemove);
		List<AnnotationNode> newAnnotationsToAdd = new ArrayList<>(annotationsToAdd);
		newAnnotationsToAdd.addAll(other.annotationsToAdd);
		Set<TypeAnnotationKey> newTypeAnnotationsToRemove = new LinkedHashSet<>(typeAnnotationsToRemove);
		newTypeAnnotationsToRemove.addAll(other.typeAnnotationsToRemove);
		List<TypeAnnotationNode> newTypeAnnotationsToAdd = new ArrayList<>(typeAnnotationsToAdd);
		Map<String, GenericAnnotationData> newFields = new LinkedHashMap<>(fields);
		other.fields.forEach((key, value) -> newFields.merge(key, value, GenericAnnotationData::merge));
		Map<String, MethodAnnotationData> newMethods = new LinkedHashMap<>(methods);
		other.methods.forEach((key, value) -> newMethods.merge(key, value, MethodAnnotationData::merge));
		return new ClassAnnotationData(newAnnotationsToRemove, newAnnotationsToAdd, newTypeAnnotationsToRemove, newTypeAnnotationsToAdd, newFields, newMethods);
	}

	ClassAnnotationData remap(String className, TinyRemapper remapper) {
		return new ClassAnnotationData(
				annotationsToRemove.stream().map(remapper.getEnvironment().getRemapper()::map).collect(Collectors.toCollection(LinkedHashSet::new)),
				annotationsToAdd.stream().map(ann -> AnnotationsData.remap(ann, remapper)).collect(Collectors.toCollection(ArrayList::new)),
				typeAnnotationsToRemove.stream().map(key -> key.remap(remapper)).collect(Collectors.toCollection(LinkedHashSet::new)),
				typeAnnotationsToAdd.stream().map(ann -> AnnotationsData.remap(ann, remapper)).collect(Collectors.toCollection(ArrayList::new)),
				AnnotationsData.remapMap(
						fields,
						entry -> remapField(className, entry.getKey(), remapper),
						entry -> entry.getValue().remap(remapper)
				),
				AnnotationsData.remapMap(
						methods,
						entry -> remapMethod(className, entry.getKey(), remapper),
						entry -> entry.getValue().remap(remapper)
				)
		);
	}

	private static String remapField(String className, String field, TinyRemapper remapper) {
		String[] nameDesc = field.split(":", 2);

		if (nameDesc.length != 2) {
			return field;
		}

		TrRemapper trRemapper = remapper.getEnvironment().getRemapper();
		return trRemapper.mapFieldName(className, nameDesc[0], nameDesc[1]) + ":" + trRemapper.mapDesc(nameDesc[1]);
	}

	private static String remapMethod(String className, String method, TinyRemapper remapper) {
		int parenIndex = method.indexOf('(');

		if (parenIndex == -1) {
			return method;
		}

		String name = method.substring(0, parenIndex);
		String desc = method.substring(parenIndex);
		TrRemapper trRemapper = remapper.getEnvironment().getRemapper();
		return trRemapper.mapMethodName(className, name, desc) + trRemapper.mapMethodDesc(desc);
	}

	public int modifyAccessFlags(int access) {
		if (annotationsToRemove.contains("java/lang/Deprecated")) {
			access &= ~Opcodes.ACC_DEPRECATED;
		}

		if (annotationsToAdd.stream().anyMatch(ann -> "Ljava/lang/Deprecated;".equals(ann.desc))) {
			access |= Opcodes.ACC_DEPRECATED;
		}

		return access;
	}

	@Nullable
	public GenericAnnotationData getFieldData(String fieldName, String fieldDesc) {
		return fields.get(fieldName + ":" + fieldDesc);
	}

	@Nullable
	public MethodAnnotationData getMethodData(String methodName, String methodDesc) {
		return methods.get(methodName + methodDesc);
	}
}
