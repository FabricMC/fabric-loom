/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016, 2017, 2018 FabricMC
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

package net.fabricmc.loom.util.enumwidener;

import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

public class EnumWidenerClassNode extends ClassNode {
	public EnumWidenerClassNode(final int api) {
		super(api);
	}

	@Override
	public void visit(final int version, final int access, final String name, final String signature, final String superName, final String[] interfaces) {
		final int widenedAccess = access & ~Opcodes.ACC_ENUM;

		if (widenedAccess == access) {
			throw new IllegalArgumentException(name + " is not an enum");
		}

		super.visit(version, widenedAccess, name, signature, superName, interfaces);
	}

	@Override
	public FieldVisitor visitField(final int access, final String name, final String descriptor, final String signature, final Object value) {
		return super.visitField(access & ~(Opcodes.ACC_ENUM | Opcodes.ACC_SYNTHETIC), name, descriptor, signature, value);
	}

	@Override
	public MethodVisitor visitMethod(final int access, final String name, final String descriptor, final String signature, final String[] exceptions) {
		return super.visitMethod(
			access,
			name,
			descriptor,
			name.equals("<init>") ? signature.replace("(", "(Ljava/lang/String;I") : signature,
			exceptions
		);
	}
}
