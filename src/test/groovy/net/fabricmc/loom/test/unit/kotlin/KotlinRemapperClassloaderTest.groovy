/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022 FabricMC
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

package net.fabricmc.loom.test.unit.kotlin

import kotlin.KotlinVersion
import kotlin.metadata.jvm.KotlinClassMetadata
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import spock.lang.Specification

import net.fabricmc.loom.util.kotlin.KotlinClasspath
import net.fabricmc.loom.util.kotlin.KotlinRemapperClassloader
import net.fabricmc.tinyremapper.api.TrClass
import net.fabricmc.tinyremapper.api.TrEnvironment
import net.fabricmc.tinyremapper.api.TrRemapper

class KotlinRemapperClassloaderTest extends Specification {
	private static final String KOTLIN_VERSION = KotlinVersion.CURRENT.toString()

	def "Test Kotlin Remapper Classloader"() {
		given:
		def classLoader = KotlinRemapperClassloader.create(new TestKotlinClasspath())
		def mockTrClass = Mock(TrClass)
		def mockEnv = Mock(TrEnvironment)
		def mockRemapper = Mock(TrRemapper)

		mockRemapper.map(_) >> { args -> args[0] }
		mockRemapper.mapMethodDesc(_) >> { args -> args[0] }

		mockEnv.remapper >> mockRemapper
		mockTrClass.environment >> mockEnv

		def classReader = new ClassReader(getClassBytes("TestExtensionKt"))

		when:
		def extension = classLoader.tinyRemapperExtension
		def visitor = extension.insertApplyVisitor(mockTrClass, new ClassNode())

		classReader.accept(visitor, 0)

		then:
		extension != null
		visitor != null

		// Ensure that the visitor is using the kotlin version specified on the classpath.
		visitor.runtimeKotlinVersion == KOTLIN_VERSION

		cleanup:
		classLoader?.close()
	}

	private static class TestKotlinClasspath implements KotlinClasspath {
		@Override
		String version() {
			return KOTLIN_VERSION
		}

		@Override
		Set<URL> classpath() {
			return Set.of(
					KotlinVersion.class.protectionDomain.codeSource.location,
					KotlinClassMetadata.class.protectionDomain.codeSource.location
					)
		}
	}

	def getClassBytes(String name) {
		return new File("src/test/resources/classes/${name}.class").bytes
	}
}
