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

package net.fabricmc.loom.test.unit.configuration

import net.fabricmc.loom.util.download.DownloadBuilder
import net.fabricmc.mappingio.MappingReader
import net.fabricmc.mappingio.format.MappingFormat
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Path

class IntermediaryMappingsProviderTest extends Specification {
	@Shared
	private static File tempDir = File.createTempDir()

	def "provide"() {
		given:
			def download = Mock(DownloadBuilder)
			def provider = new MockIntermediaryMappingsProvider(download)

			download.defaultCache() >> download
			download.downloadPath(_) >> { Path path ->
				MockIntermediaryMappingsProvider.writeIntermediaryJar(path)
			}

		when:
			def intermediaryPath = new File(tempDir, "mappings.tiny").toPath()
			provider.provide(intermediaryPath)

		then:
			MappingReader.detectFormat(intermediaryPath) == MappingFormat.TINY_2
	}

	def "read metadata"() {
		given:
			def download = Mock(DownloadBuilder)
			def provider = new MockIntermediaryMappingsProvider(download)

			download.defaultCache() >> download
			download.downloadPath(_) >> { Path path ->
				MockIntermediaryMappingsProvider.writeIntermediaryJar(path, manifest)
			}

		when:
			def metadata = provider.metadata

		then:
			metadata == (manifest + ["Manifest-Version": "1.0"])

		where:
			manifest                                                    | _
			[:]      								                    | _ // Current shipping
			["Game-Id": "23w13a", "Game-Version": "1.20-alpha.23.13.a"] | _ // Newer intermediary
	}
}
