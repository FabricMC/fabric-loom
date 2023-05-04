/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2023 FabricMC
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

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

public class SnowmanClassVisitor extends ClassVisitor {
	public static class SnowmanMethodVisitor extends MethodVisitor {
		public SnowmanMethodVisitor(int api, MethodVisitor methodVisitor) {
			super(api, methodVisitor);
		}

		@Override
		public void visitParameter(final String name, final int access) {
			if (name != null && name.startsWith("\u2603")) {
				super.visitParameter(null, access);
			} else {
				super.visitParameter(name, access);
			}
		}

		@Override
		public void visitLocalVariable(
				final String name,
				final String descriptor,
				final String signature,
				final Label start,
				final Label end,
				final int index) {
			String newName = name;

			if (name != null && name.startsWith("\u2603")) {
				newName = "lvt" + index;
			}

			super.visitLocalVariable(newName, descriptor, signature, start, end, index);
		}
	}

	public SnowmanClassVisitor(int api, ClassVisitor cv) {
		super(api, cv);
	}

	@Override
	public void visitSource(final String source, final String debug) {
		// Don't trust the obfuscation on this.
		super.visitSource(null, null);
	}

	@Override
	public MethodVisitor visitMethod(
			final int access,
			final String name,
			final String descriptor,
			final String signature,
			final String[] exceptions) {
		return new SnowmanMethodVisitor(api, super.visitMethod(access, name, descriptor, signature, exceptions));
	}
}
