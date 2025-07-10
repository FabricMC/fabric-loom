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

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.RecordComponentVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.tree.AnnotationNode;
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
		return new AnnotationsApplyClassVisitor(next, cls.getName(), annotationsData);
	}

	public static class AnnotationsApplyClassVisitor extends ClassVisitor {
		private final ClassAnnotationData classData;
		private boolean hasAddedAnnotations;

		public AnnotationsApplyClassVisitor(ClassVisitor cv, String className, AnnotationsData annotationsData) {
			super(Constants.ASM_VERSION, cv);
			this.classData = annotationsData.classes().get(className);
			hasAddedAnnotations = false;
		}

		@Override
		public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
			if (classData != null) {
				access = classData.modifyAccessFlags(access);
			}

			super.visit(version, access, name, signature, superName, interfaces);
		}

		@Override
		public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
			if (classData != null && classData.typeAnnotationsToRemove().contains(new TypeAnnotationKey(typeRef, typePath.toString(), Type.getType(descriptor).getInternalName()))) {
				return null;
			}

			return super.visitTypeAnnotation(typeRef, typePath, descriptor, visible);
		}

		@Override
		public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
			if (classData != null && classData.annotationsToRemove().contains(Type.getType(descriptor).getInternalName())) {
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

			RecordComponentVisitor rcv = super.visitRecordComponent(name, descriptor, signature);

			if (rcv == null) {
				return null;
			}

			GenericAnnotationData fieldData = classData.getFieldData(name, descriptor);

			if (fieldData == null) {
				return rcv;
			}

			return new RecordComponentVisitor(Constants.ASM_VERSION, rcv) {
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
			};
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

			return new FieldVisitor(Constants.ASM_VERSION, fv) {
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
			};
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
			addClassAnnotations();

			MethodAnnotationData methodData = classData.getMethodData(name, descriptor);

			if (methodData == null) {
				return super.visitMethod(access, name, descriptor, signature, exceptions);
			}

			MethodVisitor mv = super.visitMethod(methodData.modifyAccessFlags(access), name, descriptor, signature, exceptions);

			if (mv == null) {
				return null;
			}

			return new MethodVisitor(Constants.ASM_VERSION, mv) {
				boolean hasAddedAnnotations = false;

				@Override
				public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
					if (methodData.annotationsToRemove().contains(Type.getType(descriptor).getInternalName())) {
						return null;
					}

					return super.visitAnnotation(descriptor, visible);
				}

				@Override
				public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
					if (methodData.typeAnnotationsToRemove().contains(new TypeAnnotationKey(typeRef, typePath.toString(), Type.getType(descriptor).getInternalName()))) {
						return null;
					}

					return super.visitTypeAnnotation(typeRef, typePath, descriptor, visible);
				}

				@Override
				public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
					GenericAnnotationData parameterData = methodData.parameters().get(parameter);

					if (parameterData != null && parameterData.annotationsToRemove().contains(Type.getType(descriptor).getInternalName())) {
						return null;
					}

					return super.visitParameterAnnotation(parameter, descriptor, visible);
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

					methodData.parameters().forEach((paramIndex, paramData) -> {
						for (AnnotationNode annotation : paramData.annotationsToAdd()) {
							AnnotationVisitor av = mv.visitParameterAnnotation(paramIndex, annotation.desc, false);

							if (av != null) {
								annotation.accept(av);
							}
						}
					});
				}
			};
		}

		@Override
		public void visitEnd() {
			addClassAnnotations();
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
}
