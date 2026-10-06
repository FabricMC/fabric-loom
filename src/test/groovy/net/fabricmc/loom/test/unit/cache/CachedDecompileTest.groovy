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

import javax.tools.ToolProvider

import spock.lang.Specification
import spock.lang.TempDir

import net.fabricmc.loom.decompilers.ClassLineNumbers
import net.fabricmc.loom.decompilers.LoomInternalDecompiler
import net.fabricmc.loom.decompilers.cache.CachedData
import net.fabricmc.loom.decompilers.cache.CachedFileStoreImpl
import net.fabricmc.loom.decompilers.cache.CachedJarProcessor
import net.fabricmc.loom.decompilers.vineflower.VineflowerDecompiler
import net.fabricmc.loom.util.FileSystemUtil

class CachedDecompileTest extends Specification {
	@TempDir
	Path testPath

	def "partial decompile includes anonymous classes inside nested classes"() {
		given:
		def input = compileFixture("original", "original")
		def cache = newProcessor()
		def seed = testPath.resolve("seed.jar")
		FileSystemUtil.getJarFileSystem(seed, true).withCloseable { fs ->
			Files.createDirectories(fs.getPath("example"))
			Files.write(fs.getPath("example/Unrelated.class"), inputClass(input, "example/Unrelated.class"))
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
		sources(output)["example/Outer.java"].contains('System.out.println("original")')

		when:
		def cachedRequest = cache.prepareJob(input)
		def cachedOutput = complete(cache, cachedRequest, "cached")

		then:
		cachedRequest.job() instanceof CachedJarProcessor.CompletedWorkJob
		sources(cachedOutput) == sources(fullOutput)
	}

	def "changing only a nested anonymous class invalidates its enclosing source"() {
		given:
		def original = compileFixture("original", "original")
		def changed = compileFixture("changed", "changed")
		def cache = newProcessor()
		complete(cache, cache.prepareJob(original), "original-sources")
		def fullOutput = testPath.resolve("full.jar")
		decompile(changed, fullOutput)

		expect:
		inputClass(original, "example/Outer.class") == inputClass(changed, "example/Outer.class")
		inputClass(original, "example/Outer\$Nested.class") == inputClass(changed, "example/Outer\$Nested.class")
		inputClass(original, "example/Outer\$Nested\$1.class") != inputClass(changed, "example/Outer\$Nested\$1.class")

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

	private Path compileFixture(String directory, String message) {
		def root = Files.createDirectories(testPath.resolve(directory))
		def source = root.resolve("Outer.java")
		Files.writeString(source, """
			package example;
			public class Outer {
				public static class Nested {
					public Runnable create() {
						return new Runnable() {
							public void run() {
								System.out.println("${message}");
							}
						};
					}
				}
			}
			class Unrelated {}
		""")
		def classes = Files.createDirectories(root.resolve("classes"))
		def result = ToolProvider.systemJavaCompiler.run(null, null, null, "--release", "17", "-d", classes.toString(), source.toString())
		assert result == 0
		def jar = root.resolve("input.jar")
		FileSystemUtil.getJarFileSystem(jar, true).withCloseable { fs ->
			Files.walk(classes).withCloseable { paths ->
				for (Path path : paths.filter { Files.isRegularFile(it) }.toList()) {
					def target = fs.getPath(classes.relativize(path).toString())
					Files.createDirectories(target.parent)
					Files.copy(path, target)
				}
			}
		}
		return jar
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
		return Files.newBufferedReader(lineMap).withCloseable { ClassLineNumbers.readMappings(it) }
	}

	private static byte[] inputClass(Path jar, String name) {
		return FileSystemUtil.getJarFileSystem(jar).withCloseable { fs -> Files.readAllBytes(fs.getPath(name)) }
	}

	private static Map<String, String> sources(Path jar) {
		Map<String, String> result = [:]
		FileSystemUtil.getJarFileSystem(jar).withCloseable { fs ->
			Files.walk(fs.root).withCloseable { paths ->
				for (Path path : paths.filter { it.toString().endsWith(".java") }.toList()) {
					result[path.toString().substring(1)] = Files.readString(path)
				}
			}
		}
		return result
	}
}
