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

import spock.lang.Specification

import net.fabricmc.loom.decompilers.cache.JarWalker
import net.fabricmc.loom.test.util.ZipTestUtils
import net.fabricmc.loom.util.FileSystemUtil

class JarWalkerTest extends Specification {
	def "find classes in jar"() {
		given:
		def jar = ZipTestUtils.createZip([
			"net/fabricmc/Test.class": "",
			"net/fabricmc/other/Test.class": "",
			"net/fabricmc/other/Test\$Inner.class": "",
			"net/fabricmc/other/Test\$1.class": "",
		])
		when:
		def entries = JarWalker.findClasses(jar)
		then:
		entries.size() == 2

		entries[0].parentClass() == "net/fabricmc/Test.class"
		entries[0].sourcesFileName() == "net/fabricmc/Test.java"
		entries[0].innerClasses().size() == 0

		entries[1].parentClass() == "net/fabricmc/other/Test.class"
		entries[1].sourcesFileName() == "net/fabricmc/other/Test.java"
		entries[1].innerClasses().size() == 2
		entries[1].innerClasses()[0] == "net/fabricmc/other/Test\$1.class"
		entries[1].innerClasses()[1] == "net/fabricmc/other/Test\$Inner.class"
	}

	def "Hash Classes"() {
		given:
		def jar = ZipTestUtils.createZip(zipEntries)
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
		"2339de144d8a4a1198adf8142b6d3421ec0baacea13c9ade42a93071b6d62e43" | [
			"net/fabricmc/Test.class": "abc123",
		]
		"1053cfadf4e371ec89ff5b58d9b3bdb80373f3179e804b2e241171223709f4d1" | [
			"net/fabricmc/other/Test.class": "Hello",
			"net/fabricmc/other/Test\$Inner.class": "World",
			"net/fabricmc/other/Test\$Inner\$2.class": "123",
			"net/fabricmc/other/Test\$1.class": "test",
		]
		"f30b705f3a921b60103a4ee9951aff59b6db87cc289ba24563743d753acff433" | [
			"net/fabricmc/other/Test.class": "Hello",
			"net/fabricmc/other/Test\$Inner.class": "World",
			"net/fabricmc/other/Test\$Inner\$2.class": "abc123",
			"net/fabricmc/other/Test\$1.class": "test",
		]
	}
}
