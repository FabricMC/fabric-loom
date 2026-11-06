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

package net.fabricmc.loom.test.unit.processor

import java.nio.file.Path

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import spock.lang.Specification
import spock.lang.TempDir

import net.fabricmc.loom.configuration.processors.JsrAnnotationRemapperProcessor
import net.fabricmc.loom.util.ZipUtils

class JsrAnnotationRemapperTest extends Specification {
	@TempDir
	Path tempDir

	def "restores #jsrAnnotation from its JetBrains equivalent"() {
		given:
		def jar = tempDir.resolve("annotated.jar")
		ZipUtils.add(jar, "example/Target.class", annotatedClass(jetbrainsAnnotation))
		def processor = new JsrAnnotationRemapperProcessor("restoreJsrAnnotations")

		when:
		processor.processJar(jar, processor.buildSpec(null), null)

		then:
		def result = new ClassNode()
		new ClassReader(ZipUtils.unpack(jar, "example/Target.class")).accept(result, 0)
		result.invisibleAnnotations*.desc == [
			"L${jsrAnnotation};".toString()
		]

		where:
		jetbrainsAnnotation | jsrAnnotation
		"org/jetbrains/annotations/NotNull" | "javax/annotation/Nonnull"
		"org/jetbrains/annotations/Nullable" | "javax/annotation/Nullable"
		"org/jetbrains/annotations/Unmodifiable" | "javax/annotation/concurrent/Immutable"
	}

	private static byte[] annotatedClass(String annotation) {
		def writer = new ClassWriter(0)
		writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "example/Target", null, "java/lang/Object", null)
		writer.visitAnnotation("L${annotation};", false).visitEnd()
		writer.visitEnd()
		return writer.toByteArray()
	}
}
