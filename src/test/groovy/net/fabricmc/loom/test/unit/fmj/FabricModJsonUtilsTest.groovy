/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022 FabricMC
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

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import org.intellij.lang.annotations.Language
import spock.lang.Specification

import net.fabricmc.loom.util.fmj.FabricModJsonUtils

class FabricModJsonUtilsTest extends Specification {
	// Test that the schemaVersion is moved to the first position
	def "optimize FMJ"() {
		given:
		// Matches LoomGradlePlugin
		def gson = new GsonBuilder().setPrettyPrinting().create()
		def json = gson.fromJson(INPUT_FMJ, JsonObject.class)
		when:
		def outputJson = FabricModJsonUtils.optimizeFmj(json)
		def output = gson.toJson(outputJson)
		then:
		output == OUTPUT_FMJ
		true
	}

	// schemaVersion is not first
	@Language("json")
	static String INPUT_FMJ = """
{
  "id": "modid",
  "version": "1.0.0",
  "name": "Example mod",
  "description": "This is an example description! Tell everyone what your mod is about!",
  "license": "CC0-1.0",
  "icon": "assets/modid/icon.png",
  "environment": "*",
  "entrypoints": {
    "main": [
      "com.example.ExampleMod"
    ],
    "client": [
      "com.example.ExampleModClient"
    ]
  },
  "schemaVersion": 1,
  "mixins": [
    "modid.mixins.json",
    {
      "config": "modid.client.mixins.json",
      "environment": "client"
    }
  ],
  "depends": {
    "fabricloader": "\\u003e\\u003d0.15.0",
    "minecraft": "~1.20.4",
    "java": "\\u003e\\u003d17",
    "fabric-api": "*"
  },
  "suggests": {
    "another-mod": "*"
  }
}

""".trim()

	// schemaVersion is first, everything else is unchanged
	@Language("json")
	static String OUTPUT_FMJ = """
{
  "schemaVersion": 1,
  "id": "modid",
  "version": "1.0.0",
  "name": "Example mod",
  "description": "This is an example description! Tell everyone what your mod is about!",
  "license": "CC0-1.0",
  "icon": "assets/modid/icon.png",
  "environment": "*",
  "entrypoints": {
    "main": [
      "com.example.ExampleMod"
    ],
    "client": [
      "com.example.ExampleModClient"
    ]
  },
  "mixins": [
    "modid.mixins.json",
    {
      "config": "modid.client.mixins.json",
      "environment": "client"
    }
  ],
  "depends": {
    "fabricloader": "\\u003e\\u003d0.15.0",
    "minecraft": "~1.20.4",
    "java": "\\u003e\\u003d17",
    "fabric-api": "*"
  },
  "suggests": {
    "another-mod": "*"
  }
}

""".trim()
}
