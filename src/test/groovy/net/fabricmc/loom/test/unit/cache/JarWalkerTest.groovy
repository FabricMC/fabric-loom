/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2024 FabricMC
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

package net.fabricmc.loom.test.unit.cache

import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import spock.lang.Specification

import net.fabricmc.loom.decompilers.cache.JarWalker
import net.fabricmc.loom.test.util.ZipTestUtils
import net.fabricmc.loom.util.FileSystemUtil

class JarWalkerTest extends Specification {
	def "find classes in jar"() {
		given:
		def jar = ZipTestUtils.createZipFromBytes([
			"net/fabricmc/Test.class": newClass("net/fabricmc/Test"),
			"net/fabricmc/other/Test.class": newClass("net/fabricmc/other/Test"),
			"net/fabricmc/other/Test\$Inner.class": newInnerClass("net/fabricmc/other/Test\$Inner", "net/fabricmc/other/Test", "Inner"),
			"net/fabricmc/other/Test\$1.class": newInnerClass("net/fabricmc/other/Test\$1", "net/fabricmc/other/Test"),
			"net/fabricmc/other/Test\$NotInner.class": newClass("net/fabricmc/other/Test\$NotInner"),
		])
		when:
		def entries = JarWalker.findClasses(jar)
		then:
		entries.size() == 3

		entries[0].name() == "net/fabricmc/Test.class"
		entries[0].sourcesFileName() == "net/fabricmc/Test.java"
		entries[0].innerClasses().size() == 0

		entries[1].name() == "net/fabricmc/other/Test\$NotInner.class"
		entries[1].sourcesFileName() == "net/fabricmc/other/Test\$NotInner.java"
		entries[1].innerClasses().size() == 0

		entries[2].name() == "net/fabricmc/other/Test.class"
		entries[2].sourcesFileName() == "net/fabricmc/other/Test.java"
		entries[2].innerClasses().size() == 2
		entries[2].innerClasses()[0] == "net/fabricmc/other/Test\$1.class"
		entries[2].innerClasses()[1] == "net/fabricmc/other/Test\$Inner.class"
	}

	def "Hash Classes"() {
		given:
		def jar = ZipTestUtils.createZipFromBytes(zipEntries)
		when:
		def entries = JarWalker.findClasses(jar)
		def hash = FileSystemUtil.getJarFileSystem(jar).withCloseable { fs ->
			return entries[0].hash(fs.root)
		}
		then:
		entries.size() == 1
		hash == expectedHash
		where:
		expectedHash | zipEntries
		"b055df8d9503b60050f6d0db387c84c47fedb4d9ed82c4f8174b4e465a9c479b" | [
			"net/fabricmc/Test.class": newClass("net/fabricmc/Test"),
		]
		"b49f74dc50847f8fefc0c6f850326bbe39ace0b381b827fe1a1f1ed1dea81330" | [
			"net/fabricmc/other/Test.class": newClass("net/fabricmc/other/Test"),
			"net/fabricmc/other/Test\$Inner.class": newInnerClass("net/fabricmc/other/Test\$Inner", "net/fabricmc/other/Test", "Inner"),
			"net/fabricmc/other/Test\$Inner\$2.class": newInnerClass("net/fabricmc/other/Test\$Inner\$2", "net/fabricmc/other/Test\$Inner", "Inner"),
			"net/fabricmc/other/Test\$1.class": newInnerClass("net/fabricmc/other/Test\$1", "net/fabricmc/other/Test"),
		]
		"b49f74dc50847f8fefc0c6f850326bbe39ace0b381b827fe1a1f1ed1dea81330" | [
			"net/fabricmc/other/Test.class": newClass("net/fabricmc/other/Test"),
			"net/fabricmc/other/Test\$Inner.class": newInnerClass("net/fabricmc/other/Test\$Inner", "net/fabricmc/other/Test", "Inner"),
			"net/fabricmc/other/Test\$Inner\$2.class": newInnerClass("net/fabricmc/other/Test\$Inner\$2", "net/fabricmc/other/Test\$Inner", "Inner"),
			"net/fabricmc/other/Test\$1.class": newInnerClass("net/fabricmc/other/Test\$1", "net/fabricmc/other/Test"),
		]
	}

	def "simple class"() {
		given:
		def jarEntries = [
			"net/fabricmc/Example.class": newClass("net/fabricmc/Example")
		]
		def jar = ZipTestUtils.createZipFromBytes(jarEntries)

		when:
		def classes = JarWalker.findClasses(jar)

		then:
		classes.size() == 1
		classes[0].name() == "net/fabricmc/Example.class"
		classes[0].innerClasses() == []
		classes[0].superClasses() == []
	}

	def "class with interfaces"() {
		given:
		def jarEntries = [
			"net/fabricmc/Example.class": newClass("net/fabricmc/Example", ["java/lang/Runnable"] as String[])
		]
		def jar = ZipTestUtils.createZipFromBytes(jarEntries)

		when:
		def classes = JarWalker.findClasses(jar)

		then:
		classes.size() == 1
		classes[0].name() == "net/fabricmc/Example.class"
		classes[0].innerClasses() == []
		classes[0].superClasses() == ["java/lang/Runnable"]
	}

	def "inner classes"() {
		given:
		def jarEntries = [
			"net/fabricmc/other/Test.class": newClass("net/fabricmc/other/Test"),
			"net/fabricmc/other/Test\$Inner.class": newInnerClass("net/fabricmc/other/Test\$Inner", "net/fabricmc/other/Test", "Inner", null, "net/fabricmc/other/Super"),
			"net/fabricmc/other/Test\$1.class": newInnerClass("net/fabricmc/other/Test\$1", "net/fabricmc/other/Test", null, ["java/lang/Runnable"] as String[]),
		]
		def jar = ZipTestUtils.createZipFromBytes(jarEntries)

		when:
		def classes = JarWalker.findClasses(jar)

		then:
		classes.size() == 1
		classes[0].name() == "net/fabricmc/other/Test.class"
		classes[0].innerClasses() == [
			"net/fabricmc/other/Test\$1.class",
			"net/fabricmc/other/Test\$Inner.class"
		]
		classes[0].superClasses() == [
			"java/lang/Runnable",
			"net/fabricmc/other/Super"
		]
	}

	private static byte[] newClass(String name, String[] interfaces = null, String superName = "java/lang/Object") {
		def writer = new ClassWriter(0)
		writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name, null, superName, interfaces)
		return writer.toByteArray()
	}

	private static byte[] newInnerClass(String name, String outerClass, String innerName = null, String[] interfaces = null, String superName = "java/lang/Object") {
		def writer = new ClassWriter(0)
		writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name, null, superName, interfaces)
		writer.visitInnerClass(name, outerClass, innerName, 0)
		if (innerName == null) {
			writer.visitOuterClass(outerClass, null, null)
		}
		return writer.toByteArray()
	}
}
