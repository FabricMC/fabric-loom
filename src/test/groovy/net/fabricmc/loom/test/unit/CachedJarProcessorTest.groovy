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

import spock.lang.Specification

import net.fabricmc.loom.decompilers.cache.CachedFileStore
import net.fabricmc.loom.decompilers.cache.CachedJarProcessor
import net.fabricmc.loom.test.util.ZipTestUtils
import net.fabricmc.loom.util.ZipUtils

class CachedJarProcessorTest extends Specification {
	static Map<String, String> jarEntries = [
		"net/fabricmc/Example.class": "",
		"net/fabricmc/other/Test.class": "",
		"net/fabricmc/other/Test\$Inner.class": "",
		"net/fabricmc/other/Test\$1.class": "",
	]

	static String ExampleHash = "abc123cd372fb85148700fa88095e3492d3f9f5beb43e555e5ff26d95f5a6adc36f8e6"
	static String TestHash = "abc123ecd40b16ec50b636a390cb8da716a22606965f14e526e3051144dd567f336bc5"

	def "prepare full work job"() {
		given:
		def jar = ZipTestUtils.createZip(jarEntries)
		def cache = Mock(CachedFileStore)
		def processor = new CachedJarProcessor(cache, "abc123")

		when:
		def workJob = processor.prepareJob(jar) as CachedJarProcessor.FullWorkJob

		then:
		workJob.outputNameMap().size() == 2

		// Expect two calls looking for the existing entry in the cache
		2 * cache.getEntry(_) >> null

		0 * _ // Strict mock
	}

	def "prepare partial work job"() {
		given:
		def jar = ZipTestUtils.createZip(jarEntries)
		def cache = Mock(CachedFileStore)
		def processor = new CachedJarProcessor(cache, "abc123")

		when:
		def workJob = processor.prepareJob(jar) as CachedJarProcessor.PartialWorkJob

		then:
		workJob.outputNameMap().size() == 1
		ZipUtils.unpackNullable(workJob.existing(), "net/fabricmc/Example.java") == "Example sources".bytes

		// Provide one cached entry
		// And then one call not finding the entry in the cache
		1 * cache.getEntry(ExampleHash) >> "Example sources".bytes
		1 * cache.getEntry(_) >> null

		0 * _ // Strict mock
	}

	def "prepare completed work job"() {
		given:
		def jar = ZipTestUtils.createZip(jarEntries)
		def cache = Mock(CachedFileStore)
		def processor = new CachedJarProcessor(cache, "abc123")

		when:
		def workJob = processor.prepareJob(jar) as CachedJarProcessor.CompletedWorkJob

		then:
		workJob.completed() != null
		ZipUtils.unpackNullable(workJob.completed(), "net/fabricmc/Example.java") == "Example sources".bytes
		ZipUtils.unpackNullable(workJob.completed(), "net/fabricmc/other/Test.java") == "Test sources".bytes

		// Provide one cached entry
		// And then two calls not finding the entry in the cache
		1 * cache.getEntry(ExampleHash) >> "Example sources".bytes
		1 * cache.getEntry(TestHash) >> "Test sources".bytes

		0 * _ // Strict mock
	}

	def "complete full work job"() {
		given:
		def jar = ZipTestUtils.createZip(jarEntries)
		def cache = Mock(CachedFileStore)
		def processor = new CachedJarProcessor(cache, "abc123")

		when:
		def workJob = processor.prepareJob(jar) as CachedJarProcessor.FullWorkJob

		// Do the work, such as decompiling.
		ZipUtils.add(workJob.output(), "net/fabricmc/Example.java", "Example sources")
		ZipUtils.add(workJob.output(), "net/fabricmc/other/Test.java", "Test sources")

		def outputJar = Files.createTempFile("loom-test-output", ".jar")
		Files.delete(outputJar)
		processor.completeJob(outputJar, workJob)

		then:
		workJob.outputNameMap().size() == 2

		ZipUtils.unpackNullable(outputJar, "net/fabricmc/Example.java") == "Example sources".bytes
		ZipUtils.unpackNullable(outputJar, "net/fabricmc/other/Test.java") == "Test sources".bytes

		// Expect two calls looking for the existing entry in the cache
		1 * cache.getEntry(ExampleHash) >> null
		1 * cache.getEntry(TestHash) >> null

		// Expect the new work to be put into the cache
		1 * cache.putEntry(ExampleHash, "Example sources".bytes)
		1 * cache.putEntry(TestHash, "Test sources".bytes)

		0 * _ // Strict mock
	}

	def "complete partial work job"() {
		given:
		def jar = ZipTestUtils.createZip(jarEntries)
		def cache = Mock(CachedFileStore)
		def processor = new CachedJarProcessor(cache, "abc123")

		when:
		def workJob = processor.prepareJob(jar) as CachedJarProcessor.PartialWorkJob

		// Do the work
		ZipUtils.add(workJob.output(), "net/fabricmc/other/Test.java", "Test sources")

		def outputJar = Files.createTempFile("loom-test-output", ".jar")
		Files.delete(outputJar)
		processor.completeJob(outputJar, workJob)

		then:
		workJob.outputNameMap().size() == 1

		ZipUtils.unpackNullable(outputJar, "net/fabricmc/Example.java") == "Example sources".bytes
		ZipUtils.unpackNullable(outputJar, "net/fabricmc/other/Test.java") == "Test sources".bytes

		// The cache already contains sources for example, but not for test
		1 * cache.getEntry(ExampleHash) >> "Example sources".bytes
		1 * cache.getEntry(TestHash) >> null

		// Expect the new work to be put into the cache
		1 * cache.putEntry(TestHash, "Test sources".bytes)

		0 * _ // Strict mock
	}

	def "complete completed work job"() {
		given:
		def jar = ZipTestUtils.createZip(jarEntries)
		def cache = Mock(CachedFileStore)
		def processor = new CachedJarProcessor(cache, "abc123")

		when:
		def workJob = processor.prepareJob(jar) as CachedJarProcessor.CompletedWorkJob

		def outputJar = Files.createTempFile("loom-test-output", ".jar")
		Files.delete(outputJar)
		processor.completeJob(outputJar, workJob)

		then:
		ZipUtils.unpackNullable(outputJar, "net/fabricmc/Example.java") == "Example sources".bytes
		ZipUtils.unpackNullable(outputJar, "net/fabricmc/other/Test.java") == "Test sources".bytes

		// The cache already contains sources for example, but not for test
		1 * cache.getEntry(ExampleHash) >> "Example sources".bytes
		1 * cache.getEntry(TestHash) >> "Test sources".bytes

		0 * _ // Strict mock
	}
}
