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

package net.fabricmc.loom.test.unit

import java.nio.file.Files

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import spock.lang.Specification

import net.fabricmc.loom.decompilers.ClassLineNumbers
import net.fabricmc.loom.decompilers.LineNumberRemapper
import net.fabricmc.loom.test.util.ZipTestUtils
import net.fabricmc.loom.util.Constants
import net.fabricmc.loom.util.ZipUtils

class LineNumberRemapperTests extends Specification {
	def "remapLinenumbers"() {
		given:
		def className = LineNumberSource.class.name.replace('.', '/')
		def input = ZipTestUtils.createZipFromBytes([(className + ".class"): getClassBytes(LineNumberSource.class)])

		// + 10 to each line number
		def entry = new ClassLineNumbers.Entry(className, 30, 40, [
			27: 37,
			29: 39,
			30: 40
		])
		def lineNumbers = new ClassLineNumbers([(className): entry])

		def outputJar = Files.createTempDirectory("loom").resolve("output.jar")

		when:
		def remapper = new LineNumberRemapper(lineNumbers)
		remapper.process(input, outputJar)

		def unpacked = ZipUtils.unpack(outputJar, className + ".class")

		then:
		readLineNumbers(getClassBytes(LineNumberSource.class)) == [27, 29, 30]
		readLineNumbers(unpacked) == [37, 39, 40]
	}

	static byte[] getClassBytes(Class<?> clazz) {
		return clazz.classLoader.getResourceAsStream(clazz.name.replace('.', '/') + ".class").withCloseable {
			it.bytes
		}
	}

	static List<Integer> readLineNumbers(byte[] clazzBytes) {
		def reader = new ClassReader(clazzBytes)
		def visitor = new LineNumberCollectorClassVisitor()
		reader.accept(visitor, 0)
		return visitor.lineNumbers
	}

	private static class LineNumberCollectorClassVisitor extends ClassVisitor {
		final List<Integer> lineNumbers = []

		protected LineNumberCollectorClassVisitor() {
			super(Constants.ASM_VERSION)
		}

		@Override
		MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
			return new LineNumberCollectorMethodVisitor(super.visitMethod(access, name, descriptor, signature, exceptions), lineNumbers)
		}
	}

	private static class LineNumberCollectorMethodVisitor extends MethodVisitor {
		final List<Integer> lineNumbers

		protected LineNumberCollectorMethodVisitor(MethodVisitor methodVisitor, List<Integer> lineNumbers) {
			super(Constants.ASM_VERSION, methodVisitor)
			this.lineNumbers = lineNumbers
		}

		@Override
		void visitLineNumber(int line, Label start) {
			super.visitLineNumber(line, start)
			lineNumbers.add(line)
		}
	}
}
