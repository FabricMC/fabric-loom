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

package net.fabricmc.loom.configuration.mods.dependency.refmap;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

import net.fabricmc.loom.util.Constants;

public class MixinRefmapInlinerClassVisitor extends ClassVisitor {
	private final MixinReferenceRemapper remapper;

	private String className = null;

	public MixinRefmapInlinerClassVisitor(MixinReferenceRemapper remapper, ClassVisitor classVisitor) {
		super(Constants.ASM_VERSION, classVisitor);
		this.remapper = remapper;
	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		this.className = name;
		super.visit(version, access, name, signature, superName, interfaces);
	}

	@Override
	public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
		AnnotationVisitor annotationVisitor = super.visitAnnotation(descriptor, visible);
		return new RefmapInlinerAnnotationVisitor(annotationVisitor);
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
		MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
		return new RefmapInlinerMethodVisitor(methodVisitor);
	}

	private class RefmapInlinerMethodVisitor extends MethodVisitor {
		private RefmapInlinerMethodVisitor(MethodVisitor methodVisitor) {
			super(MixinRefmapInlinerClassVisitor.super.api, methodVisitor);
		}

		@Override
		public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
			AnnotationVisitor annotationVisitor = super.visitAnnotation(descriptor, visible);
			return new RefmapInlinerAnnotationVisitor(annotationVisitor);
		}
	}

	private class RefmapInlinerAnnotationVisitor extends AnnotationVisitor {
		private RefmapInlinerAnnotationVisitor(AnnotationVisitor annotationVisitor) {
			super(MixinRefmapInlinerClassVisitor.super.api, annotationVisitor);
		}

		@Override
		public void visit(String name, Object value) {
			if (value instanceof String strValue) {
				value = remapper.remapReference(className, strValue);
			}

			super.visit(name, value);
		}

		@Override
		public AnnotationVisitor visitArray(String name) {
			AnnotationVisitor annotationVisitor = super.visitArray(name);
			return new RefmapInlinerAnnotationVisitor(annotationVisitor);
		}

		@Override
		public AnnotationVisitor visitAnnotation(String name, String descriptor) {
			AnnotationVisitor annotationVisitor = super.visitAnnotation(name, descriptor);
			return new RefmapInlinerAnnotationVisitor(annotationVisitor);
		}
	}
}
