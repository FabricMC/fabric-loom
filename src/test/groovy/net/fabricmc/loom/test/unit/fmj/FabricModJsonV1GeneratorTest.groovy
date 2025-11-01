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

	def "named contributor"() {
		given:
		def spec = baseSpec()
		spec.contributor("Epic Modder")

		when:
		def json = FabricModJsonV1Generator.INSTANCE.generate(spec)

		then:
		json == j("""
		{
		  "schemaVersion": 1,
		  "id": "examplemod",
		  "version": "1.0.0",
		  "contributors": [
		    "Epic Modder"
		  ]
		}
		""")
		tryParse(json) == 1
	}

	def "contributor with contact info"() {
		given:
		def spec = baseSpec()
		spec.contributor("Epic Modder") {
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
		  "contributors": [
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

	def "contact info"() {
		given:
		def spec = baseSpec()
		spec.contactInformation.set(["discord": "epicmodder#1234", "email": "epicmodder@example.com"])

		when:
		def json = FabricModJsonV1Generator.INSTANCE.generate(spec)

		then:
		json == j("""
		{
		  "schemaVersion": 1,
		  "id": "examplemod",
		  "version": "1.0.0",
		  "contact": {
		    "discord": "epicmodder#1234",
		    "email": "epicmodder@example.com"
		  }
		}
		""")
		tryParse(json) == 1
	}

	def "provides"() {
		given:
		def spec = baseSpec()
		spec.provides.set(['oldid', 'veryoldid'])

		when:
		def json = FabricModJsonV1Generator.INSTANCE.generate(spec)

		then:
		json == j("""
		{
		  "schemaVersion": 1,
		  "id": "examplemod",
		  "version": "1.0.0",
		  "provides": [
		    "oldid",
		    "veryoldid"
		  ]
		}
		""")
		tryParse(json) == 1
	}

	def "environment"() {
		given:
		def spec = baseSpec()
		spec.environment.set("client")

		when:
		def json = FabricModJsonV1Generator.INSTANCE.generate(spec)

		then:
		json == j("""
		{
		  "schemaVersion": 1,
		  "id": "examplemod",
		  "version": "1.0.0",
		  "environment": "client"
		}
		""")
		tryParse(json) == 1
	}

	def "jars"() {
		given:
		def spec = baseSpec()
		spec.jars.set(["libs/some-lib.jar"])

		when:
		def json = FabricModJsonV1Generator.INSTANCE.generate(spec)

		then:
		json == j("""
		{
		  "schemaVersion": 1,
		  "id": "examplemod",
		  "version": "1.0.0",
		  "jars": [
		    {
		      "file": "libs/some-lib.jar"
		    }
		  ]
		}
		""")
		tryParse(json) == 1
	}

	def "entrypoints"() {
		given:
		def spec = baseSpec()
		spec.entrypoint("main", "com.example.Main")
		spec.entrypoint("main", "com.example.Blocks")
		spec.entrypoint("client", "com.example.KotlinClient::init") {
			it.adapter.set("kotlin")
		}
		spec.entrypoint("client") {
			it.value.set("com.example.Client")
		}

		when:
		def json = FabricModJsonV1Generator.INSTANCE.generate(spec)

		then:
		json == j("""
		{
		  "schemaVersion": 1,
		  "id": "examplemod",
		  "version": "1.0.0",
		  "entrypoints": {
		    "client": [
		      {
		        "value": "com.example.KotlinClient::init",
		        "adapter": "kotlin"
		      },
		      "com.example.Client"
		    ],
		    "main": [
		      "com.example.Main",
		      "com.example.Blocks"
		    ]
		  }
		}
		""")
		tryParse(json) == 1
	}

	def "mixins"() {
		given:
		def spec = baseSpec()
		spec.mixin("mymod.mixins.json")
		spec.mixin("mymod.client.mixins.json") {
			it.environment.set("client")
		}

		when:
		def json = FabricModJsonV1Generator.INSTANCE.generate(spec)

		then:
		json == j("""
		{
		  "schemaVersion": 1,
		  "id": "examplemod",
		  "version": "1.0.0",
		  "mixins": [
		    "mymod.mixins.json",
		    {
		      "config": "mymod.client.mixins.json",
		      "environment": "client"
		    }
		  ]
		}
		""")
		tryParse(json) == 1
	}

	def "access widener"() {
		given:
		def spec = baseSpec()
		spec.accessWidener.set("mymod.accesswidener")

		when:
		def json = FabricModJsonV1Generator.INSTANCE.generate(spec)

		then:
		json == j("""
		{
		  "schemaVersion": 1,
		  "id": "examplemod",
		  "version": "1.0.0",
		  "accessWidener": "mymod.accesswidener"
		}
		""")
		tryParse(json) == 1
	}

	def "depends"() {
		given:
		def spec = baseSpec()
		spec.depends("fabricloader", ">=0.14.0")
		spec.depends("fabric-api", [">=0.14.0", "<0.15.0"])

		when:
		def json = FabricModJsonV1Generator.INSTANCE.generate(spec)

		then:
		json == j("""
		{
		  "schemaVersion": 1,
		  "id": "examplemod",
		  "version": "1.0.0",
		  "depends": {
		    "fabricloader": "\\u003e\\u003d0.14.0",
		    "fabric-api": [
		      "\\u003e\\u003d0.14.0",
		      "\\u003c0.15.0"
		    ]
		  }
		}
		""")
		tryParse(json) == 1
	}

	def "single icon"() {
		given:
		def spec = baseSpec()
		spec.icon("icon.png")

		when:
		def json = FabricModJsonV1Generator.INSTANCE.generate(spec)

		then:
		json == j("""
		{
		  "schemaVersion": 1,
		  "id": "examplemod",
		  "version": "1.0.0",
		  "icon": "icon.png"
		}
		""")
		tryParse(json) == 1
	}

	def "multiple icons"() {
		given:
		def spec = baseSpec()
		spec.icon(64, "icon_64.png")
		spec.icon(128, "icon_128.png")

		when:
		def json = FabricModJsonV1Generator.INSTANCE.generate(spec)

		then:
		json == j("""
		{
		  "schemaVersion": 1,
		  "id": "examplemod",
		  "version": "1.0.0",
		  "icon": {
		    "64": "icon_64.png",
		    "128": "icon_128.png"
		  }
		}
		""")
		tryParse(json) == 1
	}

	def "language adapters"() {
		given:
		def spec = baseSpec()
		spec.languageAdapters.put("kotlin", "net.fabricmc.loader.api.language.KotlinAdapter")

		when:
		def json = FabricModJsonV1Generator.INSTANCE.generate(spec)

		then:
		json == j("""
		{
		  "schemaVersion": 1,
		  "id": "examplemod",
		  "version": "1.0.0",
		  "languageAdapters": {
		    "kotlin": "net.fabricmc.loader.api.language.KotlinAdapter"
		  }
		}
		""")
		tryParse(json) == 1
	}

	def "custom data"() {
		given:
		def spec = baseSpec()
		spec.customData.put("examplemap", ["custom": "data"])
		spec.customData.put("examplelist", [1, 2, 3])

		when:
		def json = FabricModJsonV1Generator.INSTANCE.generate(spec)

		then:
		json == j("""
		{
		  "schemaVersion": 1,
		  "id": "examplemod",
		  "version": "1.0.0",
		  "custom": {
		    "examplemap": {
		      "custom": "data"
		    },
		    "examplelist": [
		      1,
		      2,
		      3
		    ]
		  }
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
		spec.contributor("Epic Modder") {
			it.contactInformation.set(["discord": "epicmodder#1234", "email": "epicmodder@example.com"])
		}
		spec.contactInformation.set(["discord": "epicmodder#1234", "email": "epicmodder@example.com"])
		spec.provides.set(['oldid', 'veryoldid'])
		spec.environment.set("client")
		spec.jars.set(["libs/some-lib.jar"])
		spec.entrypoint("main", "com.example.Main")
		spec.entrypoint("main", "com.example.Blocks")
		spec.entrypoint("client", "com.example.KotlinClient::init") {
			it.adapter.set("kotlin")
		}
		spec.entrypoint("client") {
			it.value.set("com.example.Client")
		}
		spec.mixin("mymod.mixins.json")
		spec.mixin("mymod.client.mixins.json") {
			it.environment.set("client")
		}
		spec.accessWidener.set("mymod.accesswidener")

		spec.depends("fabricloader", ">=0.14.0")
		spec.depends("fabric-api", [">=0.14.0", "<0.15.0"])
		spec.recommends("recommended-mod", ">=1.0.0")
		spec.suggests("suggested-mod", ">=1.0.0")
		spec.conflicts("conflicting-mod", "<1.0.0")
		spec.breaks("broken-mod", "<1.0.0")

		spec.icon(64, "icon_64.png")
		spec.icon(128, "icon_128.png")
		spec.languageAdapters.put("kotlin", "net.fabricmc.loader.api.language.KotlinAdapter")
		spec.customData.put("examplemap", ["custom": "data"])
		spec.customData.put("examplelist", [1, 2, 3])

		when:
		def json = FabricModJsonV1Generator.INSTANCE.generate(spec)

		then:
		json == j("""
		{
		  "schemaVersion": 1,
		  "id": "examplemod",
		  "version": "1.0.0",
		  "provides": [
		    "oldid",
		    "veryoldid"
		  ],
		  "environment": "client",
		  "entrypoints": {
		    "client": [
		      {
		        "value": "com.example.KotlinClient::init",
		        "adapter": "kotlin"
		      },
		      "com.example.Client"
		    ],
		    "main": [
		      "com.example.Main",
		      "com.example.Blocks"
		    ]
		  },
		  "jars": [
		    {
		      "file": "libs/some-lib.jar"
		    }
		  ],
		  "mixins": [
		    "mymod.mixins.json",
		    {
		      "config": "mymod.client.mixins.json",
		      "environment": "client"
		    }
		  ],
		  "accessWidener": "mymod.accesswidener",
		  "depends": {
		    "fabricloader": "\\u003e\\u003d0.14.0",
		    "fabric-api": [
		      "\\u003e\\u003d0.14.0",
		      "\\u003c0.15.0"
		    ]
		  },
		  "recommends": {
		    "recommended-mod": "\\u003e\\u003d1.0.0"
		  },
		  "suggests": {
		    "suggested-mod": "\\u003e\\u003d1.0.0"
		  },
		  "conflicts": {
		    "conflicting-mod": "\\u003c1.0.0"
		  },
		  "breaks": {
		    "broken-mod": "\\u003c1.0.0"
		  },
		  "name": "Example Mod",
		  "description": "This is an example mod.",
		  "authors": [
		    {
		      "name": "Epic Modder",
		      "contact": {
		        "discord": "epicmodder#1234",
		        "email": "epicmodder@example.com"
		      }
		    }
		  ],
		  "contributors": [
		    {
		      "name": "Epic Modder",
		      "contact": {
		        "discord": "epicmodder#1234",
		        "email": "epicmodder@example.com"
		      }
		    }
		  ],
		  "contact": {
		    "discord": "epicmodder#1234",
		    "email": "epicmodder@example.com"
		  },
		  "license": [
		    "MIT",
		    "Apache-2.0"
		  ],
		  "icon": {
		    "64": "icon_64.png",
		    "128": "icon_128.png"
		  },
		  "languageAdapters": {
		    "kotlin": "net.fabricmc.loader.api.language.KotlinAdapter"
		  },
		  "custom": {
		    "examplemap": {
		      "custom": "data"
		    },
		    "examplelist": [
		      1,
		      2,
		      3
		    ]
		  }
		}
        """)
		tryParse(json) == 1
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
