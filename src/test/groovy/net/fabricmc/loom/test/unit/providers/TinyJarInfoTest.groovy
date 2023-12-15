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

package net.fabricmc.loom.test.unit.providers

import java.nio.file.Path
import java.util.jar.Attributes
import java.util.jar.Manifest

import spock.lang.Specification
import spock.lang.TempDir

import net.fabricmc.loom.configuration.providers.mappings.tiny.TinyJarInfo
import net.fabricmc.loom.util.ZipUtils

class TinyJarInfoTest extends Specification {
	@TempDir
	Path tempDir
	Path v1MappingsJar
	Path v2MappingsJar

	def setup() {
		v1MappingsJar = tempDir.resolve('mappings-v1.jar')
		v2MappingsJar = tempDir.resolve('mappings-v2.jar')
		ZipUtils.add(v1MappingsJar, 'mappings/mappings.tiny', 'v1\tintermediary\tnamed\n')
		ZipUtils.add(v2MappingsJar, 'mappings/mappings.tiny', 'tiny\t2\t0\tintermediary\tnamed\n')
	}

	def "v1 without minecraft version"() {
		when:
		def jarInfo = TinyJarInfo.get(v1MappingsJar)

		then:
		jarInfo == new TinyJarInfo(false, Optional.empty())
	}

	def "v2 without minecraft version"() {
		when:
		def jarInfo = TinyJarInfo.get(v2MappingsJar)

		then:
		jarInfo == new TinyJarInfo(true, Optional.empty())
	}

	def "v1 with minecraft version"() {
		setup:
		def manifest = new Manifest()
		manifest.mainAttributes.put(Attributes.Name.MANIFEST_VERSION, '1.0')
		manifest.mainAttributes.putValue('Minecraft-Version-Id', '18w50a')
		def out = new ByteArrayOutputStream()
		manifest.write(out)
		ZipUtils.add(v1MappingsJar, 'META-INF/MANIFEST.MF', out.toByteArray())

		when:
		def jarInfo = TinyJarInfo.get(v1MappingsJar)

		then:
		jarInfo == new TinyJarInfo(false, Optional.of('18w50a'))
	}

	def "v2 with minecraft version"() {
		setup:
		def manifest = new Manifest()
		manifest.mainAttributes.put(Attributes.Name.MANIFEST_VERSION, '1.0')
		manifest.mainAttributes.putValue('Minecraft-Version-Id', '18w50a')
		def out = new ByteArrayOutputStream()
		manifest.write(out)
		ZipUtils.add(v2MappingsJar, 'META-INF/MANIFEST.MF', out.toByteArray())

		when:
		def jarInfo = TinyJarInfo.get(v2MappingsJar)

		then:
		jarInfo == new TinyJarInfo(true, Optional.of('18w50a'))
	}
}
