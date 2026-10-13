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

package net.fabricmc.loom.test.unit.processor

import java.nio.file.Files
import java.nio.file.Path

import spock.lang.Specification
import spock.lang.TempDir

import net.fabricmc.loom.configuration.accesswidener.AccessWidenerJarProcessor
import net.fabricmc.loom.configuration.accesswidener.ModAccessWidenerEntry
import net.fabricmc.loom.util.Checksum
import net.fabricmc.loom.util.fmj.FabricModJson
import net.fabricmc.loom.util.fmj.FabricModJsonSource
import net.fabricmc.loom.util.fmj.ModEnvironment

class ModAccessWidenerEntryTest extends Specification {
	@TempDir
	Path modDirectory

	def "read local mod"() {
		given:
		def accessWidener = modDirectory.resolve("test.accesswidener")
		Files.writeString(accessWidener, "accessWidener v2 named\n")
		def mod = Mock(FabricModJson.Mockable)
		mod.getClassTweakers() >> ["test.accesswidener": ModEnvironment.UNIVERSAL]
		mod.getSource() >> new FabricModJsonSource.DirectorySource(modDirectory)

		when:
		def entries = ModAccessWidenerEntry.readAll(mod, true)
		then:
		entries.size() == 1
		def entry = entries[0]

		entry.path() == "test.accesswidener"
		entry.environment() == ModEnvironment.UNIVERSAL
		entry.transitiveOnly()
		entry.hash() == Checksum.of(accessWidener).sha1().hex()
	}

	def "cache hash tracks access widener contents"() {
		given:
		def accessWidener = modDirectory.resolve("test.accesswidener")
		Files.writeString(accessWidener, "accessWidener v2 named\ntransitive-accessible class test/Original\n")
		def mod = Mock(FabricModJson.Mockable)
		mod.getId() >> "testmod"
		mod.getClassTweakers() >> ["test.accesswidener": ModEnvironment.UNIVERSAL]
		mod.getSource() >> new FabricModJsonSource.DirectorySource(modDirectory)
		def originalEntry = ModAccessWidenerEntry.readAll(mod, true)[0]
		def originalHash = originalEntry.hashCode()
		def originalSpec = new AccessWidenerJarProcessor.Spec([originalEntry])
		def originalSpecHash = originalSpec.hashCode()

		when:
		def unchangedEntry = ModAccessWidenerEntry.readAll(mod, true)[0]
		Files.writeString(accessWidener, "accessWidener v2 named\ntransitive-accessible class test/Changed\n")
		def changedEntry = ModAccessWidenerEntry.readAll(mod, true)[0]

		then:
		unchangedEntry.hashCode() == originalHash
		changedEntry.hashCode() != originalHash
		new AccessWidenerJarProcessor.Spec([changedEntry]).hashCode() != originalSpecHash
		originalEntry.hashCode() == originalHash
		originalSpec.hashCode() == originalSpecHash
	}
}
