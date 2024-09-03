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

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import spock.lang.Specification
import spock.lang.TempDir

import net.fabricmc.loom.decompilers.ClassLineNumbers
import net.fabricmc.loom.decompilers.cache.CachedData
import net.fabricmc.loom.decompilers.cache.CachedFileStore
import net.fabricmc.loom.decompilers.cache.CachedFileStoreImpl
import net.fabricmc.loom.decompilers.cache.CachedJarProcessor
import net.fabricmc.loom.test.util.ZipTestUtils
import net.fabricmc.loom.util.ZipUtils

class CachedJarProcessorTest extends Specification {
	static Map<String, byte[]> jarEntries = [
		"net/fabricmc/Example.class": newClass("net/fabricmc/Example"),
		"net/fabricmc/other/Test.class": newClass("net/fabricmc/other/Test"),
		"net/fabricmc/other/Test\$Inner.class": newInnerClass("net/fabricmc/other/Test\$Inner", "net/fabricmc/other/Test", "Inner"),
		"net/fabricmc/other/Test\$1.class": newInnerClass("net/fabricmc/other/Test\$1", "net/fabricmc/other/Test"),
	]

	static String ExampleHash = "abc123/db5c3a2d04e0c6ea03aef0d217517aa0233f9b8198753d3c96574fe5825a13c4"
	static String TestHash = "abc123/b49f74dc50847f8fefc0c6f850326bbe39ace0b381b827fe1a1f1ed1dea81330"

	static CachedData ExampleCachedData = new CachedData("net/fabricmc/Example", "Example sources", lineNumber("net/fabricmc/Example"))
	static CachedData TestCachedData = new CachedData("net/fabricmc/other/Test", "Test sources", lineNumber("net/fabricmc/other/Test"))

	@TempDir
	Path testPath

	def "prepare full work job"() {
		given:
		def jar = ZipTestUtils.createZipFromBytes(jarEntries)
		def cache = Mock(CachedFileStore)
		def processor = new CachedJarProcessor(cache, "abc123")

		when:
		def workRequest = processor.prepareJob(jar)
		def workJob = workRequest.job() as CachedJarProcessor.FullWorkJob

		then:
		workRequest.lineNumbers() == null
		workJob.outputNameMap().size() == 2

		// Expect two calls looking for the existing entry in the cache
		2 * cache.getEntry(_) >> null

		0 * _ // Strict mock
	}

	def "prepare partial work job"() {
		given:
		def jar = ZipTestUtils.createZipFromBytes(jarEntries)
		def cache = Mock(CachedFileStore)
		def processor = new CachedJarProcessor(cache, "abc123")

		when:
		def workRequest = processor.prepareJob(jar)
		def workJob = workRequest.job() as CachedJarProcessor.PartialWorkJob
		def lineMap = workRequest.lineNumbers().lineMap()

		then:
		lineMap.size() == 1
		lineMap.get("net/fabricmc/Example") == ExampleCachedData.lineNumbers()

		workJob.outputNameMap().size() == 1
		ZipUtils.unpackNullable(workJob.existingSources(), "net/fabricmc/Example.java") == "Example sources".bytes
		ZipUtils.unpackNullable(workJob.existingClasses(), "net/fabricmc/Example.class") == newClass("net/fabricmc/Example")

		// Provide one cached entry
		// And then one call not finding the entry in the cache
		1 * cache.getEntry(ExampleHash) >> ExampleCachedData
		1 * cache.getEntry(_) >> null

		0 * _ // Strict mock
	}

	def "prepare completed work job"() {
		given:
		def jar = ZipTestUtils.createZipFromBytes(jarEntries)
		def cache = Mock(CachedFileStore)
		def processor = new CachedJarProcessor(cache, "abc123")

		when:
		def workRequest = processor.prepareJob(jar)
		def workJob = workRequest.job() as CachedJarProcessor.CompletedWorkJob
		def lineMap = workRequest.lineNumbers().lineMap()

		then:
		lineMap.size() == 2
		lineMap.get("net/fabricmc/Example") == ExampleCachedData.lineNumbers()
		lineMap.get("net/fabricmc/other/Test") == TestCachedData.lineNumbers()

		workJob.completed() != null
		ZipUtils.unpackNullable(workJob.completed(), "net/fabricmc/Example.java") == "Example sources".bytes
		ZipUtils.unpackNullable(workJob.completed(), "net/fabricmc/other/Test.java") == "Test sources".bytes

		// Provide one cached entry
		// And then two calls not finding the entry in the cache
		1 * cache.getEntry(ExampleHash) >> ExampleCachedData
		1 * cache.getEntry(TestHash) >> TestCachedData

		0 * _ // Strict mock
	}

	def "complete full work job"() {
		given:
		def jar = ZipTestUtils.createZipFromBytes(jarEntries)
		def cache = Mock(CachedFileStore)
		def processor = new CachedJarProcessor(cache, "abc123")

		when:
		def workRequest = processor.prepareJob(jar)
		def workJob = workRequest.job() as CachedJarProcessor.FullWorkJob

		// Do the work, such as decompiling.
		ZipUtils.add(workJob.output(), "net/fabricmc/Example.java", "Example sources")
		ZipUtils.add(workJob.output(), "net/fabricmc/other/Test.java", "Test sources")

		def outputJar = Files.createTempFile("loom-test-output", ".jar")
		Files.delete(outputJar)

		ClassLineNumbers lineNumbers = lineNumbers([
			"net/fabricmc/Example",
			"net/fabricmc/other/Test"
		])
		processor.completeJob(outputJar, workJob, lineNumbers)

		then:
		workJob.outputNameMap().size() == 2

		ZipUtils.unpackNullable(outputJar, "net/fabricmc/Example.java") == "Example sources".bytes
		ZipUtils.unpackNullable(outputJar, "net/fabricmc/other/Test.java") == "Test sources".bytes

		// Expect two calls looking for the existingSources entry in the cache
		1 * cache.getEntry(ExampleHash) >> null
		1 * cache.getEntry(TestHash) >> null

		// Expect the new work to be put into the cache
		1 * cache.putEntry(ExampleHash, ExampleCachedData)
		1 * cache.putEntry(TestHash, TestCachedData)

		0 * _ // Strict mock
	}

	def "complete partial work job"() {
		given:
		def jar = ZipTestUtils.createZipFromBytes(jarEntries)
		def cache = Mock(CachedFileStore)
		def processor = new CachedJarProcessor(cache, "abc123")

		when:
		def workRequest = processor.prepareJob(jar)
		def workJob = workRequest.job() as CachedJarProcessor.PartialWorkJob

		// Do the work
		ZipUtils.add(workJob.output(), "net/fabricmc/other/Test.java", "Test sources")

		def outputJar = Files.createTempFile("loom-test-output", ".jar")
		Files.delete(outputJar)

		ClassLineNumbers lineNumbers = lineNumbers([
			"net/fabricmc/Example",
			"net/fabricmc/other/Test"
		])
		processor.completeJob(outputJar, workJob, lineNumbers)

		then:
		workJob.outputNameMap().size() == 1

		ZipUtils.unpackNullable(outputJar, "net/fabricmc/Example.java") == "Example sources".bytes
		ZipUtils.unpackNullable(outputJar, "net/fabricmc/other/Test.java") == "Test sources".bytes

		// The cache already contains sources for example, but not for test
		1 * cache.getEntry(ExampleHash) >> ExampleCachedData
		1 * cache.getEntry(TestHash) >> null

		// Expect the new work to be put into the cache
		1 * cache.putEntry(TestHash, TestCachedData)

		0 * _ // Strict mock
	}

	def "complete completed work job"() {
		given:
		def jar = ZipTestUtils.createZipFromBytes(jarEntries)
		def cache = Mock(CachedFileStore)
		def processor = new CachedJarProcessor(cache, "abc123")

		when:
		def workRequest = processor.prepareJob(jar)
		def workJob = workRequest.job() as CachedJarProcessor.CompletedWorkJob

		def outputJar = Files.createTempFile("loom-test-output", ".jar")
		Files.delete(outputJar)

		ClassLineNumbers lineNumbers = lineNumbers([
			"net/fabricmc/Example",
			"net/fabricmc/other/Test"
		])
		processor.completeJob(outputJar, workJob, lineNumbers)

		then:
		ZipUtils.unpackNullable(outputJar, "net/fabricmc/Example.java") == "Example sources".bytes
		ZipUtils.unpackNullable(outputJar, "net/fabricmc/other/Test.java") == "Test sources".bytes

		// The cache already contains sources for example, but not for test
		1 * cache.getEntry(ExampleHash) >> ExampleCachedData
		1 * cache.getEntry(TestHash) >> TestCachedData

		0 * _ // Strict mock
	}

	def "hierarchy change invalidates cache"() {
		given:
		def jar1 = ZipTestUtils.createZipFromBytes(
				[
					"net/fabricmc/Example.class": newClass("net/fabricmc/Example"),
					"net/fabricmc/other/Test.class": newClass("net/fabricmc/other/Test", ),
					"net/fabricmc/other/Test\$Inner.class": newInnerClass("net/fabricmc/other/Test\$Inner", "net/fabricmc/other/Test", "Inner", ["net/fabricmc/Example"] as String[]),
					"net/fabricmc/other/Test\$1.class": newInnerClass("net/fabricmc/other/Test\$1", "net/fabricmc/other/Test"),
				]
				)
		// The second jar changes Example, so we would expect Test to be invalidated, thus causing a full decompile in this case
		def jar2 = ZipTestUtils.createZipFromBytes(
				[
					"net/fabricmc/Example.class": newClass("net/fabricmc/Example", ["java/lang/Runnable"] as String[]),
					"net/fabricmc/other/Test.class": newClass("net/fabricmc/other/Test", ),
					"net/fabricmc/other/Test\$Inner.class": newInnerClass("net/fabricmc/other/Test\$Inner", "net/fabricmc/other/Test", "Inner", ["net/fabricmc/Example"] as String[]),
					"net/fabricmc/other/Test\$1.class": newInnerClass("net/fabricmc/other/Test\$1", "net/fabricmc/other/Test"),
				]
				)

		def cache = new CachedFileStoreImpl<>(testPath.resolve("cache"), CachedData.SERIALIZER, new CachedFileStoreImpl.CacheRules(50_000, Duration.ofDays(90)))
		def processor = new CachedJarProcessor(cache, "abc123")

		when:
		def workRequest = processor.prepareJob(jar1)
		def workJob = workRequest.job() as CachedJarProcessor.FullWorkJob
		def outputSourcesJar = workJob.output()

		// Do the work, such as decompiling.
		ZipUtils.add(outputSourcesJar, "net/fabricmc/Example.java", "Example sources")
		ZipUtils.add(outputSourcesJar, "net/fabricmc/other/Test.java", "Test sources")

		// Complete the job
		def outputJar = Files.createTempFile("loom-test-output", ".jar")
		Files.delete(outputJar)

		ClassLineNumbers lineNumbers = lineNumbers([
			"net/fabricmc/Example",
			"net/fabricmc/other/Test"
		])
		processor.completeJob(outputJar, workJob, lineNumbers)

		workRequest = processor.prepareJob(jar2)
		def newWorkJob = workRequest.job()

		then:
		newWorkJob instanceof CachedJarProcessor.FullWorkJob
	}

	private static ClassLineNumbers lineNumbers(List<String> names) {
		return new ClassLineNumbers(names.collectEntries { [it, lineNumber(it)] })
	}

	private static ClassLineNumbers.Entry lineNumber(String name) {
		return new ClassLineNumbers.Entry(name, 0, 0, [:])
	}

	private static byte[] newClass(String name, String[] interfaces = null, String superName = "java/lang/Object") {
		def writer = new ClassWriter(0)
		writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name, null, superName, interfaces)
		return writer.toByteArray()
	}

	private static byte[] newInnerClass(String name, String outerClass, String innerName = null, String[] interfaces = null, String superName = "java/lang/Object") {
		def writer = new ClassWriter(0)
		writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name, null, superName, interfaces)
		writer.visitInnerClass(name, outerClass, innerName, 0)
		if (innerName == null) {
			writer.visitOuterClass(outerClass, null, null)
		}
		return writer.toByteArray()
	}
}
