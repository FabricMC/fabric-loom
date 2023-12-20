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

class FabricModJsonV0Test extends Specification {
	// I think this is the old v0 format ¯\_(ツ)_/¯
	@Language("json")
	static String JSON = """
{
	"id": "example-mod-id",
	"name": "Example mod name for testing",
	"version": "1.0.0",
	"side": "universal",
	"initializers": [
	],
	"mixins": {
		"client": "mixins.client.json",
		"common": [
			"mixins.common.json"
		]
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
		fmj.version == 0
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
		fmj.mixinConfigurations == [
			"mixins.client.json",
			"mixins.common.json"
		]
	}

	// Not supported in this version
	def "injected interfaces"() {
		given:
		def mockSource = Mock(FabricModJsonSource)
		when:
		def fmj = FabricModJsonFactory.create(JSON_OBJECT, mockSource)
		def jsonObject = fmj.getCustom(Constants.CustomModJsonKeys.INJECTED_INTERFACE)
		then:
		jsonObject == null
	}

	// Not supported in this version
	def "class tweaker"() {
		given:
		def mockSource = Mock(FabricModJsonSource)
		when:
		def fmj = FabricModJsonFactory.create(JSON_OBJECT, mockSource)
		then:
		fmj.getClassTweakers() == [:]
	}

	def "hash code"() {
		given:
		def mockSource = Mock(FabricModJsonSource)
		when:
		def fmj = FabricModJsonFactory.create(JSON_OBJECT, mockSource)
		then:
		fmj.hashCode() == 930565976
	}
}
