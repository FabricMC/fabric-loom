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

import java.nio.file.Files
import java.nio.file.Path

import spock.lang.Specification
import spock.lang.TempDir

import net.fabricmc.loom.util.JarPackageIndex
import net.fabricmc.loom.util.Pair
import net.fabricmc.loom.util.ZipUtils

class JarPackageIndexTest extends Specification {
	@TempDir
	Path dir

	def "Create JarPackageIndex from single JAR"() {
		given:
		def jar = zip([
			"com/example/Foo.class",
			"com/example/Bar.class",
			"com/example/subpackage/Baz.class"
		])

		when:
		def index = JarPackageIndex.create([jar])

		then:
		index.packages().size() == 2
		index.packages()["com.example"] == ["Bar", "Foo"]
		index.packages()["com.example.subpackage"] == ["Baz"]
	}

	def "Create JarPackageIndex from multiple JARs"() {
		given:
		def jar1 = zip([
			"com/example/Foo.class",
			"com/example/Bar.class"
		])
		def jar2 = zip([
			"com/example/subpackage/Baz.class",
			"com/another/Example.class"
		])

		when:
		def index = JarPackageIndex.create([jar1, jar2])

		then:
		index.packages().size() == 3
		index.packages()["com.example"] == ["Bar", "Foo"]
		index.packages()["com.example.subpackage"] == ["Baz"]
		index.packages()["com.another"] == ["Example"]
	}

	def "Handle empty JAR"() {
		given:
		def jar = zip([])

		when:
		def index = JarPackageIndex.create([jar])

		then:
		index.packages().isEmpty()
	}

	private Path zip(List<String> entries) {
		def zip = Files.createTempFile(dir, "loom", ".zip")
		Files.delete(zip)

		def files = entries.stream().map {
			new Pair<>(it, new byte[0])
		}.toList()

		ZipUtils.add(zip, files)
		return zip
	}
}
