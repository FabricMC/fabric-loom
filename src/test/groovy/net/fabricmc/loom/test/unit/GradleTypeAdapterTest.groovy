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

import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import spock.lang.IgnoreIf
import spock.lang.Specification

import net.fabricmc.loom.util.gradle.GradleTypeAdapter

class GradleTypeAdapterTest extends Specification {
	def "Property"() {
		given:
		def property = Mock(Property)

		when:
		def json = GradleTypeAdapter.GSON.toJson(property)

		then:
		1 * property.isPresent() >> true
		1 * property.get() >> "value"
		json == "\"value\""
	}

	def "Empty Property"() {
		given:
		def property = Mock(Property)

		when:
		def json = GradleTypeAdapter.GSON.toJson(property)

		then:
		1 * property.isPresent() >> false
		json == "null"
	}

	@IgnoreIf({ os.windows })
	def "FileCollection"() {
		given:
		def file1 = new File("file1")
		def file2 = new File("file2")
		def fileCollection = Mock(FileCollection)

		when:
		def json = GradleTypeAdapter.GSON.toJson(fileCollection)

		then:
		1 * fileCollection.getFiles() >> [file1, file2].shuffled()
		json == "[\"${file1.getAbsolutePath()}\",\"${file2.getAbsolutePath()}\"]"
	}

	@IgnoreIf({ os.windows })
	def "RegularFileProperty"() {
		given:
		def file = new File("file")
		def regularFileProperty = Mock(RegularFileProperty)
		def regularFile = Mock(RegularFile)

		when:
		def json = GradleTypeAdapter.GSON.toJson(regularFileProperty)

		then:
		1 * regularFileProperty.isPresent() >> true
		1 * regularFileProperty.get() >> regularFile
		1 * regularFile.getAsFile() >> file
		json == "\"${file.getAbsolutePath()}\""
	}

	def "Empty RegularFileProperty"() {
		given:
		def regularFileProperty = Mock(RegularFileProperty)

		when:
		def json = GradleTypeAdapter.GSON.toJson(regularFileProperty)

		then:
		1 * regularFileProperty.isPresent() >> false
		json == "null"
	}

	def "ListProperty"() {
		given:
		def listProperty = Mock(ListProperty)
		def list = ["value1", "value2"]

		when:
		def json = GradleTypeAdapter.GSON.toJson(listProperty)

		then:
		1 * listProperty.get() >> list
		json == "[\"value1\",\"value2\"]"
	}

	def "MapProperty"() {
		given:
		def mapProperty = Mock(MapProperty)
		def map = ["key1": "value1", "key2": "value2"]

		when:
		def json = GradleTypeAdapter.GSON.toJson(mapProperty)

		then:
		1 * mapProperty.get() >> map
		json == "{\"key1\":\"value1\",\"key2\":\"value2\"}"
	}
}
