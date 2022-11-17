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

package net.fabricmc.loom.test.unit.classtweaker

import net.fabricmc.classtweaker.api.ClassTweaker
import net.fabricmc.loom.configuration.classtweaker.ClassTweakerFactory
import net.fabricmc.loom.configuration.classtweaker.ClassTweakerJarTransformer
import net.fabricmc.loom.test.unit.classtweaker.classes.TestClass
import net.fabricmc.loom.test.unit.classtweaker.classes.TestEnum
import net.fabricmc.loom.test.util.FileSystemTestTrait
import spock.lang.Specification

import java.lang.reflect.Modifier

class ClassTweakerJarTransformerTest extends Specification implements FileSystemTestTrait {
	def "transform"() {
		given:
			def testClass = copyClass(TestClass.class)
			def testEnum = copyClass(TestEnum.class)

			def input = """
classTweaker\tv1\tsomenamespace
accessible\tmethod\t$testClass\thello\t()V
extend-enum\t$testEnum\tADDED\t(Ljava/lang/String;ILjava/lang/String;)V
\toverride\thello\tnet/fabricmc/classtweaker/StaticClass\thello\t(I)V
""".trim()
			def classTweaker = createClassTweaker(input)

		when:
			def inputClass = readClass(testClass)
			def inputEnum = readClass(testEnum)

			new ClassTweakerJarTransformer(classTweaker).transform(delegate)

			def outputClass = readClass(testClass)
			def outputEnum = readClass(testEnum)
		then:
			!Modifier.isPublic(inputClass.methods[1].access)
			Modifier.isPublic(outputClass.methods[1].access)

			inputEnum.fields.size() == 2
			outputEnum.fields.size() == 3
	}

	private ClassTweaker createClassTweaker(String input) {
		def instance = ClassTweaker.newInstance()
		ClassTweakerFactory.DEFAULT.read(instance, input.bytes, "test")
		return instance
	}
}
