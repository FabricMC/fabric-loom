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

package net.fabricmc.loom.configuration.providers.minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.RecordComponentVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.TypeReference;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeAnnotationNode;

import net.fabricmc.loom.configuration.providers.mappings.extras.annotations.AnnotationsData;
import net.fabricmc.loom.configuration.providers.mappings.extras.annotations.ClassAnnotationData;
import net.fabricmc.loom.configuration.providers.mappings.extras.annotations.GenericAnnotationData;
import net.fabricmc.loom.configuration.providers.mappings.extras.annotations.MethodAnnotationData;
import net.fabricmc.loom.configuration.providers.mappings.extras.annotations.TypeAnnotationKey;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.api.TrClass;

public record AnnotationsApplyVisitor(AnnotationsData annotationsData) implements TinyRemapper.ApplyVisitorProvider {
	@Override
	public ClassVisitor insertApplyVisitor(TrClass cls, ClassVisitor next) {
		ClassAnnotationData classData = annotationsData.classes().get(cls.getName());

		if (classData == null) {
			return next;
		}

		return new AnnotationsApplyClassVisitor(next, classData);
	}

	public static class AnnotationsApplyClassVisitor extends ClassVisitor {
		private final ClassAnnotationData classData;
		private boolean isRecord;
		private final List<MethodNode> storedRecordMethods = new ArrayList<>();
		private final StringBuilder canonicalConstructorDesc = new StringBuilder("(");
		private final List<@Nullable GenericAnnotationData> recordComponentAnnotationData = new ArrayList<>();
		private boolean hasAddedAnnotations;

		public AnnotationsApplyClassVisitor(ClassVisitor cv, ClassAnnotationData classData) {
			super(Constants.ASM_VERSION, cv);
			this.classData = classData;
			hasAddedAnnotations = false;
		}

		@Override
		public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
			access = classData.modifyAccessFlags(access);
			isRecord = (access & Opcodes.ACC_RECORD) != 0;
			super.visit(version, access, name, signature, superName, interfaces);
		}

		@Override
		public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
			if (classData.typeAnnotationsToRemove().contains(new TypeAnnotationKey(typeRef, typePath.toString(), Type.getType(descriptor).getInternalName()))) {
				return null;
			}

			return super.visitTypeAnnotation(typeRef, typePath, descriptor, visible);
		}

		@Override
		public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
			if (classData.annotationsToRemove().contains(Type.getType(descriptor).getInternalName())) {
				return null;
			}

			return super.visitAnnotation(descriptor, visible);
		}

		@Override
		public void visitNestMember(String nestMember) {
			addClassAnnotations();
			super.visitNestMember(nestMember);
		}

		@Override
		public void visitPermittedSubclass(String permittedSubclass) {
			addClassAnnotations();
			super.visitPermittedSubclass(permittedSubclass);
		}

		@Override
		public void visitInnerClass(String name, String outerName, String innerName, int access) {
			addClassAnnotations();
			super.visitInnerClass(name, outerName, innerName, access);
		}

		@Override
		public RecordComponentVisitor visitRecordComponent(String name, String descriptor, String signature) {
			addClassAnnotations();

			GenericAnnotationData fieldData = classData.getFieldData(name, descriptor);
			canonicalConstructorDesc.append(descriptor);
			recordComponentAnnotationData.add(fieldData);

			RecordComponentVisitor rcv = super.visitRecordComponent(name, descriptor, signature);

			if (rcv == null) {
				return null;
			}

			if (fieldData == null) {
				return rcv;
			}

			return new ApplyRecordComponentVisitor(rcv, fieldData);
		}

		@Override
		public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
			addClassAnnotations();

			GenericAnnotationData fieldData = classData.getFieldData(name, descriptor);

			if (fieldData == null) {
				return super.visitField(access, name, descriptor, signature, value);
			}

			FieldVisitor fv = super.visitField(fieldData.modifyAccessFlags(access), name, descriptor, signature, value);

			if (fv == null) {
				return null;
			}

			return new ApplyFieldVisitor(fv, fieldData);
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
			addClassAnnotations();

			if (isRecord) {
				MethodNode node = new MethodNode(access, name, descriptor, signature, exceptions);
				storedRecordMethods.add(node);
				return node;
			}

			return visitMethodInner(access, name, descriptor, signature, exceptions);
		}

		private MethodVisitor visitMethodInner(int access, String name, String descriptor, String signature, String[] exceptions) {
			MethodAnnotationData methodData = classData.getMethodData(name, descriptor);

			if (isRecord && "<init>".equals(name) && canonicalConstructorDesc.toString().equals(descriptor)) {
				MethodVisitor mv = super.visitMethod(methodData != null ? methodData.modifyAccessFlags(access) : access, name, descriptor, signature, exceptions);
				return new CanonicalConstructorApplyVisitor(mv, methodData, descriptor, recordComponentAnnotationData);
			}

			if (methodData != null) {
				MethodVisitor mv = super.visitMethod(methodData.modifyAccessFlags(access), name, descriptor, signature, exceptions);

				if (mv == null) {
					return null;
				}

				return new ApplyMethodVisitor(mv, methodData, descriptor);
			}

			if (isRecord && descriptor.startsWith("()")) {
				GenericAnnotationData fieldData = classData.getFieldData(name, descriptor.substring(2));

				if (fieldData != null) {
					MethodVisitor mv = super.visitMethod(fieldData.modifyAccessFlags(access), name, descriptor, signature, exceptions);

					if (mv == null) {
						return null;
					}

					return new RecordComponentGetterApplyVisitor(mv, fieldData);
				}
			}

			return super.visitMethod(access, name, descriptor, signature, exceptions);
		}

		@Override
		public void visitEnd() {
			addClassAnnotations();

			canonicalConstructorDesc.append(")V");

			for (MethodNode method : storedRecordMethods) {
				MethodVisitor mv = visitMethodInner(method.access, method.name, method.desc, method.signature, method.exceptions == null ? null : method.exceptions.toArray(new String[0]));

				if (mv != null) {
					method.accept(mv);
				}
			}

			super.visitEnd();
		}

		private void addClassAnnotations() {
			if (hasAddedAnnotations) {
				return;
			}

			hasAddedAnnotations = true;

			for (AnnotationNode annotation : classData.annotationsToAdd()) {
				AnnotationVisitor av = cv.visitAnnotation(annotation.desc, false);

				if (av != null) {
					annotation.accept(av);
				}
			}

			for (TypeAnnotationNode typeAnnotation : classData.typeAnnotationsToAdd()) {
				AnnotationVisitor av = cv.visitTypeAnnotation(typeAnnotation.typeRef, typeAnnotation.typePath, typeAnnotation.desc, false);

				if (av != null) {
					typeAnnotation.accept(av);
				}
			}
		}
	}

	private static class ApplyRecordComponentVisitor extends RecordComponentVisitor {
		private final GenericAnnotationData fieldData;

		ApplyRecordComponentVisitor(RecordComponentVisitor rcv, GenericAnnotationData fieldData) {
			super(Constants.ASM_VERSION, rcv);
			this.fieldData = fieldData;
		}

		@Override
		public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
			if (fieldData.annotationsToRemove().contains(Type.getType(descriptor).getInternalName())) {
				return null;
			}

			return super.visitAnnotation(descriptor, visible);
		}

		@Override
		public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
			if (fieldData.typeAnnotationsToRemove().contains(new TypeAnnotationKey(typeRef, typePath.toString(), Type.getType(descriptor).getInternalName()))) {
				return null;
			}

			return super.visitTypeAnnotation(typeRef, typePath, descriptor, visible);
		}

		@Override
		public void visitEnd() {
			for (AnnotationNode annotation : fieldData.annotationsToAdd()) {
				AnnotationVisitor av = delegate.visitAnnotation(annotation.desc, false);

				if (av != null) {
					annotation.accept(av);
				}
			}

			for (TypeAnnotationNode typeAnnotation : fieldData.typeAnnotationsToAdd()) {
				AnnotationVisitor av = delegate.visitTypeAnnotation(typeAnnotation.typeRef, typeAnnotation.typePath, typeAnnotation.desc, false);

				if (av != null) {
					typeAnnotation.accept(av);
				}
			}

			super.visitEnd();
		}
	}

	private static class ApplyFieldVisitor extends FieldVisitor {
		private final GenericAnnotationData fieldData;

		ApplyFieldVisitor(FieldVisitor fv, GenericAnnotationData fieldData) {
			super(Constants.ASM_VERSION, fv);
			this.fieldData = fieldData;
		}

		@Override
		public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
			if (fieldData.annotationsToRemove().contains(Type.getType(descriptor).getInternalName())) {
				return null;
			}

			return super.visitAnnotation(descriptor, visible);
		}

		@Override
		public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
			if (fieldData.typeAnnotationsToRemove().contains(new TypeAnnotationKey(typeRef, typePath.toString(), Type.getType(descriptor).getInternalName()))) {
				return null;
			}

			return super.visitTypeAnnotation(typeRef, typePath, descriptor, visible);
		}

		@Override
		public void visitEnd() {
			for (AnnotationNode annotation : fieldData.annotationsToAdd()) {
				AnnotationVisitor av = fv.visitAnnotation(annotation.desc, false);

				if (av != null) {
					annotation.accept(av);
				}
			}

			for (TypeAnnotationNode typeAnnotation : fieldData.typeAnnotationsToAdd()) {
				AnnotationVisitor av = fv.visitTypeAnnotation(typeAnnotation.typeRef, typeAnnotation.typePath, typeAnnotation.desc, false);

				if (av != null) {
					typeAnnotation.accept(av);
				}
			}

			super.visitEnd();
		}
	}

	private abstract static class AbstractApplyMethodVisitor extends MethodVisitor {
		@Nullable
		protected final MethodAnnotationData methodData;
		private final String descriptor;
		private int syntheticParameterCount = 0;
		private boolean visitedAnnotableParameterCount = false;
		private boolean hasAddedAnnotations = false;

		AbstractApplyMethodVisitor(MethodVisitor mv, @Nullable MethodAnnotationData methodData, String descriptor) {
			super(Constants.ASM_VERSION, mv);
			this.methodData = methodData;
			this.descriptor = descriptor;
		}

		@Nullable
		protected abstract GenericAnnotationData getParameterData(int parameterIndex);

		protected abstract boolean hasAnnotableParameters();

		protected abstract void forEachParameterData(BiConsumer<Integer, GenericAnnotationData> action);

		@Override
		public void visitParameter(String name, int access) {
			if ((access & Opcodes.ACC_SYNTHETIC) != 0) {
				syntheticParameterCount++;
			}

			super.visitParameter(name, access);
		}

		@Override
		public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
			if (methodData != null && methodData.annotationsToRemove().contains(Type.getType(descriptor).getInternalName())) {
				return null;
			}

			return super.visitAnnotation(descriptor, visible);
		}

		@Override
		public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
			if (methodData != null && methodData.typeAnnotationsToRemove().contains(new TypeAnnotationKey(typeRef, typePath.toString(), Type.getType(descriptor).getInternalName()))) {
				return null;
			}

			return super.visitTypeAnnotation(typeRef, typePath, descriptor, visible);
		}

		@Override
		public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
			GenericAnnotationData parameterData = getParameterData(parameter);

			if (parameterData != null && parameterData.annotationsToRemove().contains(Type.getType(descriptor).getInternalName())) {
				return null;
			}

			return super.visitParameterAnnotation(parameter, descriptor, visible);
		}

		@Override
		public void visitAnnotableParameterCount(int parameterCount, boolean visible) {
			if (!visible && hasAnnotableParameters()) {
				parameterCount = Math.max(parameterCount, Type.getArgumentCount(descriptor) - syntheticParameterCount);
				visitedAnnotableParameterCount = true;
			}

			super.visitAnnotableParameterCount(parameterCount, visible);
		}

		@Override
		public void visitCode() {
			addMethodAnnotations();
			super.visitCode();
		}

		@Override
		public void visitEnd() {
			addMethodAnnotations();
			super.visitEnd();
		}

		void addMethodAnnotations() {
			if (hasAddedAnnotations) {
				return;
			}

			hasAddedAnnotations = true;

			if (methodData != null) {
				for (AnnotationNode annotation : methodData.annotationsToAdd()) {
					AnnotationVisitor av = mv.visitAnnotation(annotation.desc, false);

					if (av != null) {
						annotation.accept(av);
					}
				}

				for (TypeAnnotationNode typeAnnotation : methodData.typeAnnotationsToAdd()) {
					AnnotationVisitor av = mv.visitTypeAnnotation(typeAnnotation.typeRef, typeAnnotation.typePath, typeAnnotation.desc, false);

					if (av != null) {
						typeAnnotation.accept(av);
					}
				}
			}

			if (!visitedAnnotableParameterCount && hasAnnotableParameters()) {
				mv.visitAnnotableParameterCount(Type.getArgumentCount(descriptor) - syntheticParameterCount, false);
				visitedAnnotableParameterCount = true;
			}

			forEachParameterData((paramIndex, paramData) -> {
				for (AnnotationNode annotation : paramData.annotationsToAdd()) {
					AnnotationVisitor av = mv.visitParameterAnnotation(paramIndex, annotation.desc, false);

					if (av != null) {
						annotation.accept(av);
					}
				}
			});
		}
	}

	private static class ApplyMethodVisitor extends AbstractApplyMethodVisitor {
		ApplyMethodVisitor(MethodVisitor mv, MethodAnnotationData methodData, String descriptor) {
			super(mv, methodData, descriptor);
		}

		@Override
		@Nullable
		protected GenericAnnotationData getParameterData(int parameterIndex) {
			return methodData == null ? null : methodData.parameters().get(parameterIndex);
		}

		@Override
		protected boolean hasAnnotableParameters() {
			return methodData != null && !methodData.parameters().isEmpty();
		}

		@Override
		protected void forEachParameterData(BiConsumer<Integer, GenericAnnotationData> action) {
			if (methodData != null) {
				methodData.parameters().forEach(action);
			}
		}
	}

	private static class CanonicalConstructorApplyVisitor extends AbstractApplyMethodVisitor {
		private final List<@Nullable GenericAnnotationData> parameterData;

		CanonicalConstructorApplyVisitor(MethodVisitor mv, @Nullable MethodAnnotationData methodData, String descriptor, List<@Nullable GenericAnnotationData> parameterData) {
			super(mv, methodData, descriptor);
			this.parameterData = parameterData;
		}

		@Override
		@Nullable
		protected GenericAnnotationData getParameterData(int parameterIndex) {
			return parameterIndex >= 0 && parameterIndex < parameterData.size() ? parameterData.get(parameterIndex) : null;
		}

		@Override
		protected boolean hasAnnotableParameters() {
			return parameterData.stream().anyMatch(Objects::nonNull);
		}

		@Override
		protected void forEachParameterData(BiConsumer<Integer, GenericAnnotationData> action) {
			for (int i = 0; i < parameterData.size(); i++) {
				GenericAnnotationData data = parameterData.get(i);

				if (data != null) {
					action.accept(i, data);
				}
			}
		}
	}

	private static class RecordComponentGetterApplyVisitor extends MethodVisitor {
		private final GenericAnnotationData fieldData;

		private RecordComponentGetterApplyVisitor(MethodVisitor mv, GenericAnnotationData fieldData) {
			super(Constants.ASM_VERSION, mv);
			this.fieldData = fieldData;
		}

		@Override
		public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
			if (fieldData.annotationsToRemove().contains(Type.getType(descriptor).getInternalName())) {
				return null;
			}

			return super.visitAnnotation(descriptor, visible);
		}

		@Override
		public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
			int fieldTypeRef = typeRef;

			if (new TypeReference(fieldTypeRef).getSort() == TypeReference.METHOD_RETURN) {
				fieldTypeRef = TypeReference.newTypeReference(TypeReference.FIELD).getValue();
			}

			if (fieldData.typeAnnotationsToRemove().contains(new TypeAnnotationKey(fieldTypeRef, typePath.toString(), Type.getType(descriptor).getInternalName()))) {
				return null;
			}

			return super.visitTypeAnnotation(typeRef, typePath, descriptor, visible);
		}

		@Override
		public void visitEnd() {
			for (AnnotationNode annotation : fieldData.annotationsToAdd()) {
				AnnotationVisitor av = mv.visitAnnotation(annotation.desc, false);

				if (av != null) {
					annotation.accept(av);
				}
			}

			for (TypeAnnotationNode typeAnnotation : fieldData.typeAnnotationsToAdd()) {
				int methodTypeRef = typeAnnotation.typeRef;

				if (new TypeReference(methodTypeRef).getSort() == TypeReference.METHOD_RETURN) {
					methodTypeRef = TypeReference.newTypeReference(TypeReference.FIELD).getValue();
				}

				AnnotationVisitor av = mv.visitTypeAnnotation(methodTypeRef, typeAnnotation.typePath, typeAnnotation.desc, false);

				if (av != null) {
					typeAnnotation.accept(av);
				}
			}

			super.visitEnd();
		}
	}
}
