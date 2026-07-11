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

package net.fabricmc.loom.test.unit.decompilers

import java.nio.file.Files
import java.nio.file.Path

import org.jetbrains.java.decompiler.main.ClassesProcessor
import org.jetbrains.java.decompiler.main.DecompilerContext
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger
import org.jetbrains.java.decompiler.struct.StructClass
import org.jetbrains.java.decompiler.struct.StructContext
import org.jetbrains.java.decompiler.struct.attr.StructGeneralAttribute
import org.jetbrains.java.decompiler.util.DataInputFullStream
import org.mockito.Mockito
import spock.lang.Specification
import spock.lang.TempDir

import net.fabricmc.fernflower.api.FabricJavadocStyle
import net.fabricmc.loom.api.decompilers.JavadocStyle
import net.fabricmc.loom.configuration.providers.mappings.unpick.UnpickMetadata
import net.fabricmc.loom.decompilers.vineflower.TinyJavadocProvider

class TinyJavadocProviderTest extends Specification {
	private static final String V2_NAME = UnpickMetadata.V2.name.replace('.', '/')

	@TempDir
	Path tempDir

	def setupSpec() {
		StructGeneralAttribute.init()
	}

	def setup() {
		DecompilerContext.currentContext = new DecompilerContext(
				[:],
				Mockito.mock(IFernflowerLogger),
				Mockito.mock(StructContext),
				Mockito.mock(ClassesProcessor),
				null
				)
	}

	def cleanup() {
		DecompilerContext.currentContext = null
	}

	def "reads parameter names from an official-only mapping tree"() {
		given:
		Path mappings = tempDir.resolve("mappings.tiny")
		Files.writeString(mappings, """\
tiny\t2\t0\tofficial
c\t${V2_NAME}
\tf\tLjava/lang/String;\tnamespace
\t\tc\tThe namespace.
\tm\t(Ljava/lang/String;Ljava/lang/String;)V\t<init>
\t\tp\t1\tnamespace
\t\t\tc\tThe namespace.
""")

		StructClass structClass = readStructClass(UnpickMetadata.V2)
		def field = structClass.getField("namespace", "Ljava/lang/String;")
		def method = structClass.getMethod("<init>", "(Ljava/lang/String;Ljava/lang/String;)V")
		def provider = new TinyJavadocProvider(mappings.toFile(), "official", JavadocStyle.MARKDOWN)

		expect:
		provider.getClassJavadocStyle(structClass) == FabricJavadocStyle.MARKDOWN
		provider.getFieldJavadocStyle(structClass, field) == FabricJavadocStyle.MARKDOWN
		provider.getMethodJavadocStyle(structClass, method) == FabricJavadocStyle.MARKDOWN
		provider.getClassDoc(structClass) == "@param namespace The namespace."
		provider.getMethodDoc(structClass, method) == "@param namespace The namespace."
	}

	def "uses the configured HTML javadoc style"() {
		given:
		Path mappings = tempDir.resolve("mappings.tiny")
		Files.writeString(mappings, "tiny\t2\t0\tofficial\n")
		StructClass structClass = readStructClass(UnpickMetadata.V2)
		def provider = new TinyJavadocProvider(mappings.toFile(), "official", JavadocStyle.HTML)

		expect:
		provider.getClassJavadocStyle(structClass) == FabricJavadocStyle.HTML
	}

	private static StructClass readStructClass(Class<?> clazz) {
		String classFile = "/${clazz.name.replace('.', '/')}.class"

		clazz.getResourceAsStream(classFile).withCloseable {
			return StructClass.create(new DataInputFullStream(it.bytes), true)
		}
	}
}
