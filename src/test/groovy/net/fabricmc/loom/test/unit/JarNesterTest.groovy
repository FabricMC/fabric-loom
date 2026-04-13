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

package net.fabricmc.loom.test.unit

import java.nio.file.Files
import java.nio.file.Path

import com.google.gson.Gson
import com.google.gson.JsonObject
import spock.lang.Specification
import spock.lang.TempDir

import net.fabricmc.loom.build.nesting.JarNester
import net.fabricmc.loom.util.Checksum
import net.fabricmc.loom.util.ZipReprocessorUtil
import net.fabricmc.loom.util.ZipUtils

class JarNesterTest extends Specification {
	private static final Gson GSON = new Gson()

	@TempDir
	Path dir

	def "skip nesting when jar list is empty"() {
		given:
		def target = makeModJar("mod.jar", '{"schemaVersion":1,"id":"mod","version":"1.0"}')

		when:
		JarNester.nestJars([], target.toFile())

		then:
		noExceptionThrown()
		def fmj = readFmj(target)
		!fmj.has("jars")
	}

	def "nest a single jar"() {
		given:
		def target = makeModJar("mod.jar", '{"schemaVersion":1,"id":"mod","version":"1.0"}')
		def nested = makeModJar("lib.jar", '{"schemaVersion":1,"id":"lib","version":"1.0"}')

		when:
		JarNester.nestJars([nested.toFile()], target.toFile())

		then:
		ZipUtils.contains(target, "META-INF/jars/lib.jar")
		def fmj = readFmj(target)
		def jars = fmj.getAsJsonArray("jars")
		jars.size() == 1
		jars[0].asJsonObject.get("file").asString == "META-INF/jars/lib.jar"
	}

	def "nest multiple jars are sorted deterministically"() {
		given:
		def target = makeModJar("mod.jar", '{"schemaVersion":1,"id":"mod","version":"1.0"}')
		def libB = makeModJar("b-lib.jar", '{"schemaVersion":1,"id":"b-lib","version":"1.0"}')
		def libA = makeModJar("a-lib.jar", '{"schemaVersion":1,"id":"a-lib","version":"1.0"}')

		when:
		// Pass in reverse alphabetical order to verify sorting
		JarNester.nestJars([libB.toFile(), libA.toFile()], target.toFile())

		then:
		def fmj = readFmj(target)
		def jars = fmj.getAsJsonArray("jars")
		jars.size() == 2
		jars[0].asJsonObject.get("file").asString == "META-INF/jars/a-lib.jar"
		jars[1].asJsonObject.get("file").asString == "META-INF/jars/b-lib.jar"
	}

	def "nest jar appends to existing jars array"() {
		given:
		def existingEntry = '{"file":"META-INF/jars/existing.jar"}'
		def target = makeModJar("mod.jar", """{"schemaVersion":1,"id":"mod","version":"1.0","jars":[${existingEntry}]}""")
		def nested = makeModJar("new.jar", '{"schemaVersion":1,"id":"new","version":"1.0"}')

		when:
		JarNester.nestJars([nested.toFile()], target.toFile())

		then:
		def fmj = readFmj(target)
		def jars = fmj.getAsJsonArray("jars")
		jars.size() == 2
		jars*.asJsonObject*.get("file")*.asString.containsAll([
			"META-INF/jars/existing.jar",
			"META-INF/jars/new.jar"
		])
	}

	def "throws when target jar is not a mod jar"() {
		given:
		def notAMod = makePlainJar("plain.jar")
		def nested = makeModJar("lib.jar", '{"schemaVersion":1,"id":"lib","version":"1.0"}')

		when:
		JarNester.nestJars([nested.toFile()], notAMod.toFile())

		then:
		def e = thrown(IllegalArgumentException)
		e.message.contains("plain.jar")
	}

	def "throws when nesting a non-mod jar"() {
		given:
		def target = makeModJar("mod.jar", '{"schemaVersion":1,"id":"mod","version":"1.0"}')
		def notAMod = makePlainJar("plain.jar")

		when:
		JarNester.nestJars([notAMod.toFile()], target.toFile())

		then:
		def e = thrown(IllegalArgumentException)
		e.message.contains("plain.jar")
	}

	def "throws when nesting two jars with the same filename"() {
		given:
		def target = makeModJar("mod.jar", '{"schemaVersion":1,"id":"mod","version":"1.0"}')
		def lib1 = makeModJar("lib.jar", '{"schemaVersion":1,"id":"lib","version":"1.0"}')

		// Pre-nest lib.jar so it already occupies META-INF/jars/lib.jar
		JarNester.nestJars([lib1.toFile()], target.toFile())

		// A second (different) jar file that happens to have the same name
		def lib2Dir = Files.createTempDirectory(dir, "lib2")
		def lib2 = lib2Dir.resolve("lib.jar")
		ZipUtils.add(lib2, "fabric.mod.json", '{"schemaVersion":1,"id":"lib2","version":"1.0"}')

		when:
		JarNester.nestJars([lib2.toFile()], target.toFile())

		then:
		def e = thrown(IllegalArgumentException)
		e.message.contains("META-INF/jars/lib.jar")
	}

	def "output is reproducible when nesting a single jar"() {
		given:
		def lib = makeReproducibleModJar("lib.jar", '{"schemaVersion":1,"id":"lib","version":"1.0"}')
		def target = makeReproducibleModJar("mod.jar", '{"schemaVersion":1,"id":"mod","version":"1.0"}')

		when:
		JarNester.nestJars([lib.toFile()], target.toFile())

		then:
		Checksum.of(target).sha256().hex() == "c3b6aaa362b372d3ed885b61ff37f275899b5bc6c5a0fff8775166694ed7f875"
	}

	def "output is reproducible regardless of input jar order"() {
		given:
		def libA = makeReproducibleModJar("a-lib.jar", '{"schemaVersion":1,"id":"a-lib","version":"1.0"}')
		def libB = makeReproducibleModJar("b-lib.jar", '{"schemaVersion":1,"id":"b-lib","version":"1.0"}')
		def target = makeReproducibleModJar("mod.jar", '{"schemaVersion":1,"id":"mod","version":"1.0"}')

		when:
		JarNester.nestJars([libB.toFile(), libA.toFile()].shuffled(), target.toFile())

		then:
		Checksum.of(target).sha256().hex() == "c309abb576c15e7146cc9c3612247b27ddfe273bb12683129da20253342ef0c4"
	}

	// --- helpers ---

	/** Builds a mod jar using constant timestamps, suitable for reproducibility tests. */
	private Path makeReproducibleModJar(String name, String fmjContent) {
		def path = dir.resolve(name)
		ZipUtils.add(path, "fabric.mod.json", fmjContent)
		ZipReprocessorUtil.reprocessZip(path, true, false)
		return path
	}

	private Path makeModJar(String name, String fmjContent) {
		def path = dir.resolve(name)
		ZipUtils.add(path, "fabric.mod.json", fmjContent)
		return path
	}

	private Path makePlainJar(String name) {
		def path = dir.resolve(name)
		ZipUtils.add(path, "some/resource.txt", "hello")
		return path
	}

	private static JsonObject readFmj(Path jar) {
		return GSON.fromJson(new String(ZipUtils.unpack(jar, "fabric.mod.json")), JsonObject)
	}
}
