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

package net.fabricmc.loom.test.unit.providers

import java.nio.file.Path

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import spock.lang.Specification

import net.fabricmc.loom.configuration.providers.minecraft.RecordAccessFixerApplyVisitor
import net.fabricmc.loom.test.LoomTestConstants
import net.fabricmc.loom.util.Constants
import net.fabricmc.loom.util.ZipUtils

class RecordAccessFixerApplyVisitorTest extends Specification {
	private static final String MC_JAR_URL = "https://piston-data.mojang.com/v1/objects/ca51bf36913a7333c055096a52a3a96fbdb11813/client.jar"

	def "fix record attributes"() {
		when:
		def mcJar = downloadJarIfNotExists(MC_JAR_URL, "23w31a.jar")

		// Broken class in 23w31a
		def adk = asClassNode(ZipUtils.unpack(mcJar, "adk.class"))

		def fixed = new ClassNode()
		adk.accept(new RecordAccessFixerApplyVisitor.Visitor(Constants.ASM_VERSION, fixed))

		if (false) {
			// Export classes for testing.
			new File("adk.class").bytes = ZipUtils.unpack(mcJar, "adk.class")
			new File("adk.fixed.class").bytes = classNodeToBytes(fixed)
		}

		then:
		!isRecord(adk)
		isRecord(fixed)
	}

	private static ClassNode asClassNode(byte[] byteArray) {
		def classNode = new ClassNode()
		def classReader = new ClassReader(byteArray)
		classReader.accept(classNode, 0)
		return classNode
	}

	private static byte[] classNodeToBytes(ClassNode classNode) {
		def classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES)
		classNode.accept(classWriter)
		return classWriter.toByteArray()
	}

	private static boolean isRecord(ClassNode classNode) {
		return (classNode.access & Opcodes.ACC_RECORD) != 0
	}

	private static Path downloadJarIfNotExists(String url, String name) {
		def dst = new File(LoomTestConstants.TEST_DIR, name)

		if (!dst.exists()) {
			dst.parentFile.mkdirs()
			dst << new URL(url).newInputStream()
		}

		return dst.toPath()
	}
}
