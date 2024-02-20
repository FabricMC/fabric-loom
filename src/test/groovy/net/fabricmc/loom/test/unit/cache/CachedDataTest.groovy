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

import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

import spock.lang.Specification
import spock.lang.TempDir

import net.fabricmc.loom.decompilers.ClassLineNumbers
import net.fabricmc.loom.decompilers.cache.CachedData

class CachedDataTest extends Specification {
	@TempDir
	Path testPath

	// Simple test to check if the CachedData class can be written and read from a file
	def "Read + Write CachedData"() {
		given:
		def lineNumberEntry = new ClassLineNumbers.Entry("net/test/TestClass", 1, 2, [1: 2, 4: 7])
		def cachedData = new CachedData("net/test/TestClass", "Example sources", lineNumberEntry)
		def path = testPath.resolve("cachedData.bin")
		when:
		// Write the cachedData to a file
		FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE).withCloseable {
			cachedData.write(it)
		}

		// And read it back
		def readCachedData = Files.newInputStream(path).withCloseable {
			return CachedData.read(it)
		}

		then:
		cachedData == readCachedData
	}
}
