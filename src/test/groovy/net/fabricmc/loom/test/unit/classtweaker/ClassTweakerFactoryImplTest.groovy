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
import net.fabricmc.classtweaker.api.visitor.ClassTweakerVisitor
import net.fabricmc.loom.configuration.classtweaker.ClassTweakerEntry
import net.fabricmc.loom.configuration.classtweaker.ClassTweakerFactory
import net.fabricmc.loom.util.fmj.FabricModJson
import net.fabricmc.loom.util.fmj.FabricModJsonSource
import net.fabricmc.loom.util.fmj.ModEnvironment
import spock.lang.Specification

class ClassTweakerFactoryImplTest extends Specification {
	def "read version"() {
		given:
			def input = "classTweaker\tv1\tsomenamespace"

		when:
			def version = ClassTweakerFactory.DEFAULT.readVersion(input.bytes)

		then:
			version == ClassTweaker.CT_V1
	}

	def "read"() {
		given:
			def input = "classTweaker\tv1\tsomenamespace"
			def classTweakerVisitor = Mock(ClassTweakerVisitor)

		when:
			ClassTweakerFactory.DEFAULT.read(classTweakerVisitor, input.bytes, "test")

		then:
			1 * classTweakerVisitor.visitHeader("somenamespace")
	}

	def "read entries"() {
		given:
			def localInput = "classTweaker\tv1\tsomenamespace\n" +
				"inject-interface\ttest/LocalClass\ttest/InterfaceTests"
			def remoteInput = "classTweaker\tv1\tsomenamespace\n" +
				"transitive-inject-interface\ttest/RemoteTClass\ttest/InterfaceTests\n" +
				"inject-interface\ttest/RemoteClass\ttest/InterfaceTests\n"

			def localMod = Mock(FabricModJson)
			def localModSource = Mock(FabricModJsonSource)
			localModSource.read("local.classtweaker") >> localInput.bytes
			localMod.getSource() >> localModSource
			localMod.getMetadataVersion() >> 2
			localMod.getId() >> "localMod"

			def remoteMod = Mock(FabricModJson)
			def remoteModSource = Mock(FabricModJsonSource)
			remoteModSource.read("remote.classtweaker") >> remoteInput.bytes
			remoteMod.getSource() >> remoteModSource
			remoteMod.getMetadataVersion() >> 2
			remoteMod.getId() >> "remotemod"

			def localEntry = new ClassTweakerEntry(localMod, "local.classtweaker", ModEnvironment.UNIVERSAL, true)
			def remoteEntry = new ClassTweakerEntry(remoteMod, "remote.classtweaker", ModEnvironment.UNIVERSAL, false)

		when:
			def classTweaker = ClassTweakerFactory.DEFAULT.readEntries([localEntry, remoteEntry])

		then:
			// Test to make sure that only the remote transitive class is included
			classTweaker.getTargets().asList() == ["test.LocalClass", "test.RemoteTClass"]
	}
}
