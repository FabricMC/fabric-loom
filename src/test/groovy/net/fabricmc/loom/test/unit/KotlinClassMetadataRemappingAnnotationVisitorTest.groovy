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

package net.fabricmc.loom.test.unit

import java.nio.file.Paths

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.util.Textifier
import org.objectweb.asm.util.TraceClassVisitor
import spock.lang.Specification

import net.fabricmc.loom.kotlin.remapping.KotlinMetadataRemappingClassVisitor
import net.fabricmc.loom.util.TinyRemapperHelper
import net.fabricmc.mappingio.MappingReader
import net.fabricmc.mappingio.tree.MemoryMappingTree
import net.fabricmc.tinyremapper.IMappingProvider
import net.fabricmc.tinyremapper.TinyRemapper

class KotlinClassMetadataRemappingAnnotationVisitorTest extends Specification {

	def "simpleTest"() {
		given:
		def inputPosInChunk = getClassBytes("PosInChunk")
		def classReader = new ClassReader(inputPosInChunk)

		def tinyRemapper = TinyRemapper.newRemapper()
				.withMappings(readMappings("PosInChunk"))
				.build()

		def inputWriter = new StringWriter()
		classReader.accept(stringWriterVisitor(inputWriter), 0)

		def remappedWriter = new StringWriter()
		classReader.accept(new KotlinMetadataRemappingClassVisitor(tinyRemapper.environment.remapper, stringWriterVisitor(remappedWriter)), 0)

		when:
		def d2In = d2(inputWriter.toString())
		def d2Out = d2(remappedWriter.toString())

		then:
		println(d2In)
		println(d2Out)
	}

	def "extensionTest"() {
		given:
		def input = getClassBytes("TestExtensionKt")
		def classReader = new ClassReader(input)

		def tinyRemapper = TinyRemapper.newRemapper()
				.withMappings(readMappings("TestExtensionKt"))
				.build()

		def inputWriter = new StringWriter()
		classReader.accept(stringWriterVisitor(inputWriter), 0)

		def remappedWriter = new StringWriter()
		classReader.accept(new KotlinMetadataRemappingClassVisitor(tinyRemapper.environment.remapper, stringWriterVisitor(remappedWriter)), 0)

		when:
		def d2In = d2(inputWriter.toString())
		def d2Out = d2(remappedWriter.toString())

		then:
		println(d2In)
		println(d2Out)
	}

	private byte[] getClassBytes(String name) {
		return new File("src/test/resources/classes/${name}.class").bytes
	}

	private IMappingProvider readMappings(String name) {
		def mappingTree = new MemoryMappingTree()
		MappingReader.read(Paths.get("src/test/resources/mappings/${name}.mappings"), mappingTree)
		return TinyRemapperHelper.create(mappingTree, "named", "intermediary", false)
	}

	private ClassVisitor stringWriterVisitor(StringWriter writer) {
		return new TraceClassVisitor(null, new TextifierImpl(), new PrintWriter(writer))
	}

	private List<String> d2(String bytecode) {
		def d2Regex = ~/d2=\{(.*)}/
		def match = d2Regex.matcher(bytecode)
		return match.find() ? match.group(1).split(",") : []
	}

	private static class TextifierImpl extends Textifier {
		TextifierImpl() {
			super(Opcodes.ASM9)
		}
	}
}