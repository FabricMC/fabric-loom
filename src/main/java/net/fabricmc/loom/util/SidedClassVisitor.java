/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021-2022 FabricMC
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

package net.fabricmc.loom.util;

import java.util.Locale;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;

import net.fabricmc.tinyremapper.TinyRemapper;

/**
 * Applies the @Environment annotation to all classes.
 */
public final class SidedClassVisitor extends ClassVisitor {
	public static final TinyRemapper.ApplyVisitorProvider CLIENT = (cls, next) -> new SidedClassVisitor("client", next);
	public static final TinyRemapper.ApplyVisitorProvider SERVER = (cls, next) -> new SidedClassVisitor("server", next);

	private static final String ENVIRONMENT_DESCRIPTOR = "Lnet/fabricmc/api/Environment;";
	private static final String SIDE_DESCRIPTOR = "Lnet/fabricmc/api/EnvType;";

	private final String side;
	private boolean hasExisting = false;

	private SidedClassVisitor(String side, ClassVisitor next) {
		super(Constants.ASM_VERSION, next);
		this.side = side;
	}

	@Override
	public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
		if (ENVIRONMENT_DESCRIPTOR.equals(descriptor)) {
			hasExisting = true;
		}

		return super.visitAnnotation(descriptor, visible);
	}

	@Override
	public void visitEnd() {
		if (!hasExisting) {
			final AnnotationVisitor annotationVisitor = visitAnnotation(ENVIRONMENT_DESCRIPTOR, true);
			annotationVisitor.visitEnum("value", SIDE_DESCRIPTOR, side.toUpperCase(Locale.ROOT));
			annotationVisitor.visitEnd();
		}

		super.visitEnd();
	}
}
