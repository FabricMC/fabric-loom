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
import java.nio.file.Path

import spock.lang.Specification

import net.fabricmc.loom.test.util.ZipTestUtils
import net.fabricmc.loom.util.AsyncZipProcessor
import net.fabricmc.loom.util.ZipUtils

class AsyncZipProcessorTest extends Specification {
	def "process async"() {
		given:
		def inputZip = ZipTestUtils.createZip(createEntries())
		def outputZip = ZipTestUtils.createZip(Collections.emptyMap())
		Files.delete(outputZip)

		when:
		// Process the input zip asynchronously, converting all entries to uppercase
		AsyncZipProcessor.processEntries(inputZip, outputZip) { Path inputEntry, Path outputEntry ->
			def str = Files.readString(inputEntry)
			Files.writeString(outputEntry, str.toUpperCase())
		}

		then:
		ZipUtils.unpack(outputZip, "file1.txt") == "FILE1".bytes
		ZipUtils.unpack(outputZip, "file500.txt") == "FILE500".bytes
		ZipUtils.unpack(outputZip, "file800.txt") == "FILE800".bytes
	}

	def "re throws"() {
		given:
		def inputZip = ZipTestUtils.createZip(createEntries())
		def outputZip = ZipTestUtils.createZip(Collections.emptyMap())
		Files.delete(outputZip)

		when:
		AsyncZipProcessor.processEntries(inputZip, outputZip) { Path inputEntry, Path outputEntry ->
			throw new IOException("Test exception")
		}

		then:
		thrown(IOException)
	}

	Map<String, String> createEntries(int count = 10000) {
		Map<String, String> entries = [:]
		for (int i = 0; i < count; i++) {
			entries.put("file" + i + ".txt", "file$i")
		}
		return entries
	}
}
