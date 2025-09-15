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
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.TypeAnnotationNode;

import net.fabricmc.tinyremapper.TinyRemapper;

public record MethodAnnotationData(
		@SerializedName("remove")
		Set<String> annotationsToRemove,
		@SerializedName("add")
		List<AnnotationNode> annotationsToAdd,
		@SerializedName("type_remove")
		Set<TypeAnnotationKey> typeAnnotationsToRemove,
		@SerializedName("type_add")
		List<TypeAnnotationNode> typeAnnotationsToAdd,
		Map<Integer, GenericAnnotationData> parameters
) {
	public MethodAnnotationData {
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

		if (parameters == null) {
			parameters = new LinkedHashMap<>();
		}
	}

	MethodAnnotationData merge(MethodAnnotationData other) {
		Set<String> newAnnotationsToRemove = new LinkedHashSet<>(annotationsToRemove);
		newAnnotationsToRemove.addAll(other.annotationsToRemove);
		List<AnnotationNode> newAnnotationsToAdd = new ArrayList<>(annotationsToAdd);
		newAnnotationsToAdd.addAll(other.annotationsToAdd);
		Set<TypeAnnotationKey> newTypeAnnotationsToRemove = new LinkedHashSet<>(typeAnnotationsToRemove);
		newTypeAnnotationsToRemove.addAll(other.typeAnnotationsToRemove);
		List<TypeAnnotationNode> newTypeAnnotationsToAdd = new ArrayList<>(typeAnnotationsToAdd);
		newTypeAnnotationsToAdd.addAll(other.typeAnnotationsToAdd);
		Map<Integer, GenericAnnotationData> newParameters = new LinkedHashMap<>(parameters);
		other.parameters.forEach((key, value) -> newParameters.merge(key, value, GenericAnnotationData::merge));
		return new MethodAnnotationData(newAnnotationsToRemove, newAnnotationsToAdd, newTypeAnnotationsToRemove, newTypeAnnotationsToAdd, newParameters);
	}

	MethodAnnotationData remap(TinyRemapper remapper) {
		return new MethodAnnotationData(
				annotationsToRemove.stream().map(remapper.getEnvironment().getRemapper()::map).collect(Collectors.toCollection(LinkedHashSet::new)),
				annotationsToAdd.stream().map(ann -> AnnotationsData.remap(ann, remapper)).collect(Collectors.toCollection(ArrayList::new)),
				typeAnnotationsToRemove.stream().map(key -> key.remap(remapper)).collect(Collectors.toCollection(LinkedHashSet::new)),
				typeAnnotationsToAdd.stream().map(ann -> AnnotationsData.remap(ann, remapper)).collect(Collectors.toCollection(ArrayList::new)),
				AnnotationsData.remapMap(
						parameters,
						Map.Entry::getKey,
						entry -> entry.getValue().remap(remapper)
				)
		);
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
}
