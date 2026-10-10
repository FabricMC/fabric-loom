/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2026 FabricMC
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

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.LdcInsnNode
import spock.lang.Specification
import spock.lang.TempDir

import net.fabricmc.loom.decompilers.ClassLineNumbers
import net.fabricmc.loom.decompilers.LoomInternalDecompiler
import net.fabricmc.loom.decompilers.cache.CachedData
import net.fabricmc.loom.decompilers.cache.CachedFileStoreImpl
import net.fabricmc.loom.decompilers.cache.CachedJarProcessor
import net.fabricmc.loom.decompilers.vineflower.VineflowerDecompiler
import net.fabricmc.loom.test.unit.DecompileCacheSource
import net.fabricmc.loom.test.unit.LineNumberSource
import net.fabricmc.loom.util.FileSystemUtil

class CachedDecompileTest extends Specification {
	private static final String OUTER_CLASS = DecompileCacheSource.name.replace('.', '/')
	private static final String UNRELATED_CLASS = LineNumberSource.name.replace('.', '/')

	@TempDir
	Path testPath

	def "partial decompile includes anonymous classes inside nested classes"() {
		given:
		def input = createFixture("original")
		def cache = newProcessor()
		def seed = testPath.resolve("seed.jar")
		try (def fs = FileSystemUtil.getJarFileSystem(seed, true)) {
			Files.createDirectories(fs.getPath(UNRELATED_CLASS).parent)
			Files.write(fs.getPath(UNRELATED_CLASS + ".class"), inputClass(input, UNRELATED_CLASS + ".class"))
		}
		complete(cache, cache.prepareJob(seed), "seed-sources")
		def fullOutput = testPath.resolve("full.jar")
		decompile(input, fullOutput)

		when:
		def request = cache.prepareJob(input)
		def output = complete(cache, request, "partial")

		then:
		request.stats() == new CachedJarProcessor.CacheStats(1, 1)
		request.job() instanceof CachedJarProcessor.PartialWorkJob
		sources(output) == sources(fullOutput)
		sources(output)[OUTER_CLASS + ".java"].contains('System.out.println("original")')

		when:
		def cachedRequest = cache.prepareJob(input)
		def cachedOutput = complete(cache, cachedRequest, "cached")

		then:
		cachedRequest.job() instanceof CachedJarProcessor.CompletedWorkJob
		sources(cachedOutput) == sources(fullOutput)
	}

	def "changing only a nested anonymous class invalidates its enclosing source"() {
		given:
		def original = createFixture("original")
		def changed = createFixture("changed", true)
		def cache = newProcessor()
		complete(cache, cache.prepareJob(original), "original-sources")
		def fullOutput = testPath.resolve("full.jar")
		decompile(changed, fullOutput)

		expect:
		inputClass(original, OUTER_CLASS + ".class") == inputClass(changed, OUTER_CLASS + ".class")
		inputClass(original, OUTER_CLASS + '$Nested.class') == inputClass(changed, OUTER_CLASS + '$Nested.class')
		inputClass(original, OUTER_CLASS + '$Nested$1.class') != inputClass(changed, OUTER_CLASS + '$Nested$1.class')
		sources(fullOutput)[OUTER_CLASS + ".java"].contains('System.out.println("changed")')

		when:
		def request = cache.prepareJob(changed)
		def output = complete(cache, request, "changed-sources")

		then:
		request.stats() == new CachedJarProcessor.CacheStats(1, 1)
		request.job() instanceof CachedJarProcessor.PartialWorkJob
		sources(output) == sources(fullOutput)
	}

	private CachedJarProcessor newProcessor() {
		def rules = new CachedFileStoreImpl.CacheRules(100, Duration.ofDays(1))
		def store = new CachedFileStoreImpl<>(testPath.resolve("cache"), CachedData.SERIALIZER, rules)
		return new CachedJarProcessor(store, "test")
	}

	private Path createFixture(String name, boolean changed = false) {
		def jar = testPath.resolve(name + ".jar")
		def classNames = [
			OUTER_CLASS,
			OUTER_CLASS + '$Nested',
			OUTER_CLASS + '$Nested$1',
			UNRELATED_CLASS
		]
		try (def fs = FileSystemUtil.getJarFileSystem(jar, true)) {
			for (String className : classNames) {
				def resource = className + ".class"
				byte[] bytes
				try (def input = getClass().classLoader.getResourceAsStream(resource)) {
					bytes = input.readAllBytes()
				}
				if (changed && className == OUTER_CLASS + '$Nested$1') {
					bytes = changeAnonymousClass(bytes)
				}
				def target = fs.getPath(resource)
				Files.createDirectories(target.parent)
				Files.write(target, bytes)
			}
		}
		return jar
	}

	private static byte[] changeAnonymousClass(byte[] bytes) {
		def classNode = new ClassNode()
		new ClassReader(bytes).accept(classNode, 0)
		for (def method : classNode.methods) {
			for (def instruction : method.instructions) {
				if (instruction instanceof LdcInsnNode && instruction.cst == "original") {
					instruction.cst = "changed"
				}
			}
		}
		def writer = new ClassWriter(0)
		classNode.accept(writer)
		return writer.toByteArray()
	}

	private Path complete(CachedJarProcessor processor, CachedJarProcessor.WorkRequest request, String name) {
		ClassLineNumbers lineNumbers = null
		if (request.job() instanceof CachedJarProcessor.WorkToDoJob) {
			def job = request.job() as CachedJarProcessor.WorkToDoJob
			def libraries = job instanceof CachedJarProcessor.PartialWorkJob ? [job.existingClasses()] : []
			lineNumbers = decompile(job.incomplete(), job.output(), libraries)
		}
		def output = testPath.resolve(name + ".jar")
		processor.completeJob(output, request.job(), lineNumbers)
		return output
	}

	private ClassLineNumbers decompile(Path input, Path output, List<Path> classPath = []) {
		def lineMap = testPath.resolve(output.fileName.toString() + ".linemap")
		def context = Mock(LoomInternalDecompiler.Context) {
			compiledJar() >> input
			sourcesDestination() >> output
			linemapDestination() >> lineMap
			numberOfThreads() >> 1
			libraries() >> classPath
			options() >> [:]
			logger() >> Stub(LoomInternalDecompiler.Logger)
		}
		new VineflowerDecompiler().decompile(context)
		try (def reader = Files.newBufferedReader(lineMap)) {
			return ClassLineNumbers.readMappings(reader)
		}
	}

	private static byte[] inputClass(Path jar, String name) {
		try (def fs = FileSystemUtil.getJarFileSystem(jar)) {
			return Files.readAllBytes(fs.getPath(name))
		}
	}

	private static Map<String, String> sources(Path jar) {
		Map<String, String> result = [:]
		try (def fs = FileSystemUtil.getJarFileSystem(jar); def paths = Files.walk(fs.root)) {
			def javaPaths = paths.filter { it.toString().endsWith(".java") }.toList()
			for (Path path : javaPaths) {
				result[path.toString().substring(1)] = Files.readString(path)
			}
		}
		return result
	}
}
