/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2023 FabricMC
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

package net.fabricmc.loom.test.integration.buildSrc.remapext

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor

class StringReplacementClassVisitor extends ClassVisitor {
	final Map<String, String> replacements

	StringReplacementClassVisitor(int api, ClassVisitor classVisitor, Map<String, String> replacements) {
		super(api, classVisitor)
		this.replacements = replacements
	}

	@Override
	MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
		def methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions)
		return new StringReplacementMethodVisitor(api, methodVisitor)
	}

	class StringReplacementMethodVisitor extends MethodVisitor {
		StringReplacementMethodVisitor(int api, MethodVisitor methodVisitor) {
			super(api, methodVisitor)
		}

		@Override
		void visitLdcInsn(Object value) {
			if (value instanceof String) {
				String replacement = replacements.get(value)
				if (replacement != null) {
					value = replacement
				}
			}

			super.visitLdcInsn(value)
		}
	}
}
