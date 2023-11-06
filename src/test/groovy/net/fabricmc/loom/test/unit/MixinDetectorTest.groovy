/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2023 FabricMC
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

import java.nio.file.Path

import groovy.json.JsonOutput
import spock.lang.Specification
import spock.lang.TempDir

import net.fabricmc.loom.configuration.mods.MixinDetector
import net.fabricmc.loom.util.FileSystemUtil

class MixinDetectorTest extends Specification {
	@TempDir
	Path tempDir

	private Path makeJar(Map<String, String> mixinConfigs) {
		def path = tempDir.resolve("test.jar")
		def fs = FileSystemUtil.getJarFileSystem(path, true)

		try {
			// Create fabric.mod.json
			def fabricModJson = JsonOutput.toJson([
				schemaVersion: 1,
				id: 'test',
				version: '1',
				mixins: mixinConfigs.keySet()
			])
			fs.getPath('fabric.mod.json').text = fabricModJson

			// Write all mixin configs
			mixinConfigs.forEach { name, content ->
				fs.getPath(name).text = content
			}
		} finally {
			fs.close()
		}

		return path
	}

	def "jar without mixins has no mixins without refmaps"() {
		setup:
		def jarPath = makeJar([:])

		when:
		def hasMixinsWithoutRefmaps = MixinDetector.hasMixinsWithoutRefmap(jarPath)

		then:
		!hasMixinsWithoutRefmaps // no mixins
	}

	def "jar with one mixin config with refmap has no mixins without refmaps"() {
		setup:
		def jarPath = makeJar([
			'test.mixins.json': JsonOutput.toJson([
				'package': 'com.example.test',
				'mixins': ['TestMixin'],
				'refmap': 'test-refmap.json'
			])
		])

		when:
		def hasMixinsWithoutRefmaps = MixinDetector.hasMixinsWithoutRefmap(jarPath)

		then:
		!hasMixinsWithoutRefmaps // no mixins with refmaps
	}

	def "jar with one mixin config without refmap has mixins without refmaps"() {
		setup:
		def jarPath = makeJar([
			'test.mixins.json': JsonOutput.toJson([
				'package': 'com.example.test',
				'mixins': ['TestMixin']
			])
		])

		when:
		def hasMixinsWithoutRefmaps = MixinDetector.hasMixinsWithoutRefmap(jarPath)

		then:
		hasMixinsWithoutRefmaps // mixins with refmaps
	}

	def "jar with mixed mixin configs has mixins without refmaps"() {
		setup:
		def jarPath = makeJar([
			'test.mixins.json': JsonOutput.toJson([
				'package': 'com.example.test',
				'mixins': ['TestMixin']
			]),
			'test2.mixins.json': JsonOutput.toJson([
				'package': 'com.example.test2',
				'mixins': ['TestMixin2'],
				'refmap': 'test2-refmap.json'
			])
		])

		when:
		def hasMixinsWithoutRefmaps = MixinDetector.hasMixinsWithoutRefmap(jarPath)

		then:
		hasMixinsWithoutRefmaps // mixins with refmaps
	}
}
