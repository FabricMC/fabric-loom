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

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import spock.lang.Specification

import net.fabricmc.loom.test.util.TestServiceFactory

class TestServiceFactoryTest extends Specification {
	def "property"() {
		setup:
		def test = TestServiceFactory.objectFactory.newInstance(PropertyTest)
		when:
		test.example.set("hello")
		then:
		test.example.isPresent()
		test.example.get() == "hello"
	}

	def "file property"() {
		setup:
		def test = TestServiceFactory.objectFactory.newInstance(FileTest)
		when:
		test.example.from(new File("test"))
		then:
		test.example.files.size() == 1
		test.example.singleFile != null
	}

	interface PropertyTest {
		Property<String> getExample()
	}

	interface FileTest {
		ConfigurableFileCollection getExample()
	}
}
