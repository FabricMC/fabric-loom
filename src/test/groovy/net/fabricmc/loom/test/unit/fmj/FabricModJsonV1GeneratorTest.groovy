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

package net.fabricmc.loom.test.unit.fmj

import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.intellij.lang.annotations.Language
import spock.lang.Specification

import net.fabricmc.loader.impl.metadata.ModMetadataParser
import net.fabricmc.loom.api.fmj.FabricModJsonV1Spec
import net.fabricmc.loom.test.util.GradleTestUtil
import net.fabricmc.loom.util.fmj.gen.FabricModJsonV1Generator

class FabricModJsonV1GeneratorTest extends Specification {
	static Project project = GradleTestUtil.mockProject()
	static ObjectFactory objectFactory = project.getObjects()

	def "minimal"() {
		given:
		def spec = objectFactory.newInstance(FabricModJsonV1Spec.class)
		spec.modId.set("examplemod")
		spec.version.set("1.0.0")

		when:
		def json = FabricModJsonV1Generator.INSTANCE.generate(spec)

		then:
		json == j("""
		{
		  "schemaVersion": 1,
		  "id": "examplemod",
		  "version": "1.0.0"
		}
		""")
		tryParse(json) == 1
	}

	def "single license"() {
		given:
		def spec = baseSpec()
		spec.licenses.add("MIT")

		when:
		def json = FabricModJsonV1Generator.INSTANCE.generate(spec)

		then:
		json == j("""
		{
		  "schemaVersion": 1,
		  "id": "examplemod",
		  "version": "1.0.0",
		  "license": "MIT"
		}
		""")
		tryParse(json) == 1
	}

	def "multiple licenses"() {
		given:
		def spec = baseSpec()
		spec.licenses.addAll("MIT", "Apache-2.0")

		when:
		def json = FabricModJsonV1Generator.INSTANCE.generate(spec)

		then:
		json == j("""
		{
		  "schemaVersion": 1,
		  "id": "examplemod",
		  "version": "1.0.0",
		  "license": [
		    "MIT",
		    "Apache-2.0"
		  ]
		}
		""")
		tryParse(json) == 1
	}

	def "named author"() {
		given:
		def spec = baseSpec()
		spec.author("Epic Modder")

		when:
		def json = FabricModJsonV1Generator.INSTANCE.generate(spec)

		then:
		json == j("""
		{
		  "schemaVersion": 1,
		  "id": "examplemod",
		  "version": "1.0.0",
		  "authors": [
		    "Epic Modder"
		  ]
		}
		""")
		tryParse(json) == 1
	}

	def "author with contact info"() {
		given:
		def spec = baseSpec()
		spec.author("Epic Modder") {
			it.contactInformation.set(["discord": "epicmodder#1234", "email": "epicmodder@example.com"])
		}

		when:
		def json = FabricModJsonV1Generator.INSTANCE.generate(spec)

		then:
		json == j("""
		{
		  "schemaVersion": 1,
		  "id": "examplemod",
		  "version": "1.0.0",
		  "authors": [
		    {
		      "name": "Epic Modder",
		      "contact": {
		        "discord": "epicmodder#1234",
		        "email": "epicmodder@example.com"
		      }
		    }
		  ]
		}
		""")
		tryParse(json) == 1
	}

	def "complete"() {
		given:
		def spec = objectFactory.newInstance(FabricModJsonV1Spec.class)
		spec.modId.set("examplemod")
		spec.version.set("1.0.0")
		spec.name.set("Example Mod")
		spec.description.set("This is an example mod.")
		spec.licenses.addAll("MIT", "Apache-2.0")
		spec.author("Epic Modder") {
			it.contactInformation.set(["discord": "epicmodder#1234", "email": "epicmodder@example.com"])
		}

		when:
		def json = FabricModJsonV1Generator.INSTANCE.generate(spec)

		then:
		json == j("""
		{
		  "schemaVersion": 1,
		  "id": "examplemod",
		  "version": "1.0.0",
		  "name": "Example Mod",
		  "description": "This is an example mod.",
		  "license": [
		    "MIT",
		    "Apache-2.0"
		  ],
		  "authors": [
		    {
		      "name": "Epic Modder",
		      "contact": {
		        "discord": "epicmodder#1234",
		        "email": "epicmodder@example.com"
		      }
		    }
		  ]
		}
		""")
	}

	// Ensure that Fabric loader can actually parse the generated JSON.
	private static int tryParse(String json) {
		def meta = new ByteArrayInputStream(json.bytes).withCloseable {
			//noinspection GroovyAccessibility
			ModMetadataParser.readModMetadata(it, false)
		}
		return meta.getSchemaVersion()
	}

	private static FabricModJsonV1Spec baseSpec() {
		def spec = objectFactory.newInstance(FabricModJsonV1Spec.class)
		spec.modId.set("examplemod")
		spec.version.set("1.0.0")
		return spec
	}

	private static String j(@Language("JSON") String json) {
		return json.stripIndent().trim()
	}
}
