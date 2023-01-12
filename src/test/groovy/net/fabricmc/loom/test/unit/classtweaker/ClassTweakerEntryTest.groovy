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

package net.fabricmc.loom.test.unit.classtweaker

import net.fabricmc.classtweaker.api.ClassTweaker
import net.fabricmc.loom.configuration.classtweaker.ClassTweakerEntry
import net.fabricmc.loom.configuration.classtweaker.ClassTweakerFactory
import net.fabricmc.loom.util.fmj.FabricModJson
import net.fabricmc.loom.util.fmj.FabricModJsonSource
import net.fabricmc.loom.util.fmj.ModEnvironment
import spock.lang.Specification

class ClassTweakerEntryTest extends Specification {
	def "read multiple from local mod"() {
		given:
			def mod = Mock(FabricModJson)
			mod.getClassTweakers() >> ["test.classtweaker": ModEnvironment.UNIVERSAL, "test.client.classtweaker": ModEnvironment.CLIENT]
		when:
			def entries = ClassTweakerEntry.readAll(mod, true)
		then:
			entries.size() == 2
			entries[0].path() == "test.classtweaker"
			entries[0].environment() == ModEnvironment.UNIVERSAL
			entries[0].local() == true

			entries[1].path() == "test.client.classtweaker"
			entries[1].environment() == ModEnvironment.CLIENT
			entries[1].local() == true
	}

	def "read multiple from remote mod"() {
		given:
			def mod = Mock(FabricModJson)
			mod.getClassTweakers() >> ["test.classtweaker": ModEnvironment.UNIVERSAL, "test.client.classtweaker": ModEnvironment.SERVER]
		when:
			def entries = ClassTweakerEntry.readAll(mod, false)
		then:
			entries.size() == 2
			entries[0].path() == "test.classtweaker"
			entries[0].environment() == ModEnvironment.UNIVERSAL
			entries[0].local() == false

			entries[1].path() == "test.client.classtweaker"
			entries[1].environment() == ModEnvironment.SERVER
			entries[1].local() == false
	}

	def "max supported version"() {
		given:
			def mod = Mock(FabricModJson)
			mod.metadataVersion >> metadata
		when:
			def entry = new ClassTweakerEntry(mod, null, null, false)
		then:
			entry.maxSupportedVersion() == ctVersion
		where:
			metadata | ctVersion
			0		 | ClassTweaker.AW_V2
			1		 | ClassTweaker.AW_V2
			2		 | ClassTweaker.CT_V1
	}

	def "read"() {
		given:
			def data = "Hello World".bytes

			def modSource = Mock(FabricModJsonSource)
			modSource.read("test.classtweaker") >> data

			def mod = Mock(FabricModJson)
			mod.metadataVersion >> 2
			mod.id >> "modid"
			mod.source >> modSource

			def factory = Mock(ClassTweakerFactory)
			factory.readVersion(data) >> ClassTweaker.CT_V1

		when:
			def entry = new ClassTweakerEntry(mod, "test.classtweaker", null, false)
			entry.read(factory, null)

		then:
			1 * factory.read(null, data, "modid")
	}

	def "read unsupported version"() {
		given:
			def data = "Hello World".bytes

			def modSource = Mock(FabricModJsonSource)
			modSource.read("test.classtweaker") >> data

			def mod = Mock(FabricModJson)
			mod.metadataVersion >> 1 // V1 FMJ trying to read a CT_V1
			mod.id >> "modid"
			mod.source >> modSource

			def factory = Mock(ClassTweakerFactory)
			factory.readVersion(data) >> ClassTweaker.CT_V1

		when:
			def entry = new ClassTweakerEntry(mod, "test.classtweaker", null, false)
			entry.read(factory, null)
		then:
			def e = thrown(UnsupportedOperationException)
			e.message == "Class tweaker 'test.classtweaker' with version '3' from mod 'modid' is not supported with by this metadata version '1'."
	}
}
