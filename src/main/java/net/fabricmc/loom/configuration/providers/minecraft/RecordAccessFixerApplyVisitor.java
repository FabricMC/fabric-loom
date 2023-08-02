/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021 FabricMC
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

import org.jetbrains.annotations.VisibleForTesting;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import net.fabricmc.loom.util.Constants;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.api.TrClass;

/**
 * Fixes: <a href="https://bugs.mojang.com/browse/MC-264564">MC-264564</a>.
 */
public final class RecordAccessFixerApplyVisitor implements TinyRemapper.ApplyVisitorProvider {
	public static RecordAccessFixerApplyVisitor INSTANCE = new RecordAccessFixerApplyVisitor();

	private static final String RECORD_SUPER_NAME = "java/lang/Record";

	private RecordAccessFixerApplyVisitor() {
	}

	@Override
	public ClassVisitor insertApplyVisitor(TrClass cls, ClassVisitor next) {
		return new Visitor(Constants.ASM_VERSION, next);
	}

	@VisibleForTesting
	public static class Visitor extends ClassVisitor {
		public Visitor(int api, ClassVisitor classVisitor) {
			super(api, classVisitor);
		}

		@Override
		public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
			if (RECORD_SUPER_NAME.equals(superName)) {
				// Any class that extends the record class should also have the record access flag.
				access |= Opcodes.ACC_RECORD;
			}

			super.visit(version, access, name, signature, superName, interfaces);
		}
	}
}
