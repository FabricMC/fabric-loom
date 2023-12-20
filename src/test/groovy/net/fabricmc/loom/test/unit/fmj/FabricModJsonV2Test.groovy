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

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.intellij.lang.annotations.Language
import spock.lang.Specification

import net.fabricmc.loom.util.Constants
import net.fabricmc.loom.util.fmj.FabricModJsonFactory
import net.fabricmc.loom.util.fmj.FabricModJsonSource
import net.fabricmc.loom.util.fmj.ModEnvironment

class FabricModJsonV2Test extends Specification {
	@Language("json")
	static String JSON = """
{
	"schemaVersion": 2,
	"id": "example-mod-id",
	"name": "Example mod name for testing",
	"version": "1.0.0",
	"environment": "client",
	"license": "Apache-2.0",
	"mixins": [
		{
		  "config": "test.client.mixins.json",
		  "environment": "client"
		},
		{
		  "config": "test.server.mixins.json",
		  "environment": "server"
		},
		"test.mixins.json"
	],
	"classTweakers": [
		{
		  "config": "client.ct",
		  "environment": "client"
		},
		{
		  "config": "server.ct",
		  "environment": "server"
		},
		"universal.ct"
	],
	"custom": {
		"loom:injected_interfaces": {
		  "net/minecraft/class_123": ["net/test/TestClass"]
		}
	}
}
"""

	static JsonObject JSON_OBJECT = new Gson().fromJson(JSON, JsonObject.class)

	def "version"() {
		given:
		def mockSource = Mock(FabricModJsonSource)
		when:
		def fmj = FabricModJsonFactory.create(JSON_OBJECT, mockSource)
		then:
		fmj.version == 2
		fmj.modVersion == "1.0.0"
	}

	def "id"() {
		given:
		def mockSource = Mock(FabricModJsonSource)
		when:
		def fmj = FabricModJsonFactory.create(JSON_OBJECT, mockSource)
		then:
		fmj.id == "example-mod-id"
	}

	def "mixins"() {
		given:
		def mockSource = Mock(FabricModJsonSource)
		when:
		def fmj = FabricModJsonFactory.create(JSON_OBJECT, mockSource)
		then:
		new ArrayList<>(fmj.mixinConfigurations).sort() == [
			"test.client.mixins.json",
			"test.server.mixins.json",
			"test.mixins.json"
		].sort()
	}

	def "injected interfaces"() {
		given:
		def mockSource = Mock(FabricModJsonSource)
		when:
		def fmj = FabricModJsonFactory.create(JSON_OBJECT, mockSource)
		def jsonObject = fmj.getCustom(Constants.CustomModJsonKeys.INJECTED_INTERFACE)
		then:
		jsonObject instanceof JsonObject
		jsonObject.has("net/minecraft/class_123")
	}

	def "class tweakers"() {
		given:
		def mockSource = Mock(FabricModJsonSource)
		when:
		def fmj = FabricModJsonFactory.create(JSON_OBJECT, mockSource)
		then:
		fmj.getClassTweakers() == [
			"client.ct": ModEnvironment.CLIENT,
			"server.ct": ModEnvironment.SERVER,
			"universal.ct": ModEnvironment.UNIVERSAL
		]
	}

	def "hash code"() {
		given:
		def mockSource = Mock(FabricModJsonSource)
		when:
		def fmj = FabricModJsonFactory.create(JSON_OBJECT, mockSource)
		then:
		fmj.hashCode() == 930565978
	}
}
