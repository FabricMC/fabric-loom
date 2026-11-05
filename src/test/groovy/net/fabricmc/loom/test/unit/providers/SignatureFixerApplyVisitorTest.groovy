/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2026 FabricMC
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

package net.fabricmc.loom.test.unit.providers

import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import spock.lang.Specification

import net.fabricmc.loom.configuration.providers.minecraft.SignatureFixerApplyVisitor

class SignatureFixerApplyVisitorTest extends Specification {
	private static final String FIXED_SIGNATURE = "<T:Ljava/lang/Object;>Ljava/lang/Object;"
	private static final String EXISTING_SIGNATURE = "<U:Ljava/lang/Object;>Ljava/lang/Object;"

	def "only supplies a signature when the class has none and a fix exists"() {
		given:
		def node = new ClassNode()
		def fixer = new SignatureFixerApplyVisitor(["example/Target": FIXED_SIGNATURE])
		def visitor = fixer.insertApplyVisitor(null, node)

		when:
		visitor.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, className, originalSignature, "java/lang/Object", null)
		visitor.visitEnd()

		then:
		node.name == className
		node.signature == expectedSignature

		where:
		className | originalSignature | expectedSignature
		"example/Target" | null | FIXED_SIGNATURE
		"example/Target" | EXISTING_SIGNATURE | EXISTING_SIGNATURE
		"example/Other" | null | null
	}
}
