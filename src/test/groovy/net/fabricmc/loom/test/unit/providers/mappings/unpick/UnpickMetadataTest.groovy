/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 FabricMC
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

package net.fabricmc.loom.test.unit.providers.mappings.unpick

import java.nio.file.Files
import java.nio.file.Path

import spock.lang.Specification
import spock.lang.TempDir

import net.fabricmc.loom.configuration.providers.mappings.unpick.UnpickMetadata

class UnpickMetadataTest extends Specification {
	@TempDir
	Path tempDir

	def "should parse V1 metadata correctly"() {
		given:
		Path metadataFile = tempDir.resolve("unpick.json")
		Files.writeString(metadataFile, """
        {
            "version": 1,
            "unpickGroup": "net.fabricmc.unpick",
            "unpickVersion": "1.0.0"
        }
        """)

		when:
		def metadata = UnpickMetadata.parse(metadataFile) as UnpickMetadata.V1

		then:
		metadata.unpickGroup() == "net.fabricmc.unpick"
		metadata.unpickVersion() == "1.0.0"
		metadata.hasConstants()
	}

	def "should parse V2 metadata correctly with constants"() {
		given:
		Path metadataFile = tempDir.resolve("unpick.json")
		Files.writeString(metadataFile, """
        {
            "version": 2,
            "namespace": "named",
            "constants": "net.fabricmc:yarn:1.21.5+build.1:constants"
        }
        """)

		when:
		def metadata = UnpickMetadata.parse(metadataFile) as UnpickMetadata.V2

		then:
		metadata.namespace() == "named"
		metadata.constants() == "net.fabricmc:yarn:1.21.5+build.1:constants"
		metadata.hasConstants()
	}

	def "should parse V2 metadata correctly without constants"() {
		given:
		Path metadataFile = tempDir.resolve("unpick.json")
		Files.writeString(metadataFile, """
        {
            "version": 2,
            "namespace": "named"
        }
        """)

		when:
		def metadata = UnpickMetadata.parse(metadataFile) as UnpickMetadata.V2

		then:
		metadata.namespace() == "named"
		metadata.constants() == null
		!metadata.hasConstants()
	}

	def "should throw exception for missing version"() {
		given:
		Path metadataFile = tempDir.resolve("unpick.json")
		Files.writeString(metadataFile, """
        {
            "unpickGroup": "net.fabricmc.unpick",
            "unpickVersion": "1.0.0"
        }
        """)

		when:
		UnpickMetadata.parse(metadataFile)

		then:
		def e = thrown(UnsupportedOperationException)
		e.message == "Missing unpick metadata version"
	}

	def "should throw exception for unsupported version"() {
		given:
		Path metadataFile = tempDir.resolve("unpick.json")
		Files.writeString(metadataFile, """
        {
            "version": 3,
            "namespace": "intermediary"
        }
        """)

		when:
		UnpickMetadata.parse(metadataFile)

		then:
		def e = thrown(UnsupportedOperationException)
		e.message == "Unsupported unpick metadata version: 3. Please update loom."
	}

	def "should throw exception for missing required fields in V1"() {
		given:
		Path metadataFile = tempDir.resolve("unpick.json")
		Files.writeString(metadataFile, """
        {
            "version": 1
        }
        """)

		when:
		UnpickMetadata.parse(metadataFile)

		then:
		def e = thrown(UnsupportedOperationException)
		e.message == "Missing unpick metadata unpickGroup"
	}
}
