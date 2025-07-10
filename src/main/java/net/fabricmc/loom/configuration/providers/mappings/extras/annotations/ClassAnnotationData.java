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

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.TypeAnnotationNode;

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
) {
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
