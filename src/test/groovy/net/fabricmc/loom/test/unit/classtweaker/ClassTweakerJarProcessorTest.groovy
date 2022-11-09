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

import net.fabricmc.loom.api.processor.ProcessorContext
import net.fabricmc.loom.api.processor.SpecContext
import net.fabricmc.loom.configuration.classtweaker.ClassTweakerEntry
import net.fabricmc.loom.configuration.classtweaker.ClassTweakerFactory
import net.fabricmc.loom.configuration.classtweaker.ClassTweakerJarProcessor
import net.fabricmc.loom.util.fmj.FabricModJson
import net.fabricmc.loom.util.fmj.ModEnvironment
import spock.lang.Specification

class ClassTweakerJarProcessorTest extends Specification {
	ClassTweakerFactory factory = Mock(ClassTweakerFactory)
	SpecContext context = Mock(SpecContext)
	ClassTweakerJarProcessor processor = new ClassTweakerJarProcessor(factory) {
		String name = "Test class tweaker"
	}

	def "spec: no mods"() {
		given:
			context.localMods() >> []
			context.modDependencies() >> []
		when:
			def spec = processor.buildSpec(context)
		then:
			spec == null
	}

	def "spec: local mod no class tweaker"() {
		given:
			def mod = Mock(FabricModJson)
			mod.getClassTweakers() >> [:]

			context.localMods() >> [mod]
			context.modDependencies() >> []
		when:
			def spec = processor.buildSpec(context)
		then:
			spec == null
	}

	def "spec: local mod"() {
		given:
			def mod = Mock(FabricModJson)
			mod.getClassTweakers() >> ["test.classtweaker": ModEnvironment.UNIVERSAL]

			context.localMods() >> [mod]
			context.modDependencies() >> []
		when:
			def spec = processor.buildSpec(context)
		then:
			spec != null
			spec.classTweakers().size() == 1
			spec.classTweakers()[0].path() == "test.classtweaker"
			spec.classTweakers()[0].environment() == ModEnvironment.UNIVERSAL
	}

	def "spec: local mod and remote mod"() {
		given:
			def mod = Mock(FabricModJson)
			mod.getClassTweakers() >> ["test.classtweaker": ModEnvironment.UNIVERSAL]

			def remoteMod = Mock(FabricModJson)
			remoteMod.getClassTweakers() >> ["remote.classtweaker": ModEnvironment.CLIENT]

			context.localMods() >> [mod]
			context.modDependencies() >> [remoteMod]
		when:
			def spec = processor.buildSpec(context)
		then:
			spec != null
			spec.classTweakers().size() == 2
			spec.classTweakers()[0].path() == "test.classtweaker"
			spec.classTweakers()[0].environment() == ModEnvironment.UNIVERSAL

			spec.classTweakers()[1].path() == "remote.classtweaker"
			spec.classTweakers()[1].environment() == ModEnvironment.CLIENT
	}

	def "process: merged jar"() {
		given:
			def context = Mock(ProcessorContext)
			context.isMerged() >> true

			def mod = Mock(FabricModJson)

			def spec = new ClassTweakerJarProcessor.Spec([
			    new ClassTweakerEntry(mod, "mod.classtweaker", ModEnvironment.UNIVERSAL, false)
			])

		when:
			processor.processJar(null, spec, context)

		then:
			1 * factory.readEntries { it.size() == 1 && it[0].path == "mod.classtweaker" }
			1 * factory.transformJar(_, _)
	}

	def "process: client jar"() {
		given:
			def context = Mock(ProcessorContext)
			context.isMerged() >> false
			context.includesServer() >> false
			context.includesClient() >> true

			def mod = Mock(FabricModJson)

			def spec = new ClassTweakerJarProcessor.Spec([
				new ClassTweakerEntry(mod, "client.classtweaker", ModEnvironment.CLIENT, false),
				new ClassTweakerEntry(mod, "server.classtweaker", ModEnvironment.SERVER, false)
			])

		when:
			processor.processJar(null, spec, context)

		then:
			1 * factory.readEntries { it.size() == 1 && it[0].path == "client.classtweaker" }
			1 * factory.transformJar(_, _)
	}

	def "process: server jar"() {
		given:
			def context = Mock(ProcessorContext)
			context.isMerged() >> false
			context.includesServer() >> true
			context.includesClient() >> false

			def mod = Mock(FabricModJson)

			def spec = new ClassTweakerJarProcessor.Spec([
				new ClassTweakerEntry(mod, "modid.classtweaker", ModEnvironment.UNIVERSAL, false),
				new ClassTweakerEntry(mod, "server.classtweaker", ModEnvironment.SERVER, false)
			])

		when:
			processor.processJar(null, spec, context)

		then:
			1 * factory.readEntries { it.size() == 2 && it[0].path == "modid.classtweaker" && it[1].path == "server.classtweaker" }
			1 * factory.transformJar(_, _)
	}
}
