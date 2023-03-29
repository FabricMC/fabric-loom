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

import spock.lang.Specification

import net.fabricmc.loom.api.processor.SpecContext
import net.fabricmc.loom.configuration.accesswidener.AccessWidenerJarProcessor
import net.fabricmc.loom.test.util.GradleTestUtil
import net.fabricmc.loom.util.fmj.FabricModJson
import net.fabricmc.loom.util.fmj.ModEnvironment

class AccessWidenerJarProcessorTest extends Specification {
	def "Local AW"() {
		given:
		def specContext = Mock(SpecContext)
		def file = new File("src/test/resources/accesswidener/AccessWidenerJarProcessorTest.accesswidener")
		def localAccessWidenerProperty = GradleTestUtil.mockRegularFileProperty(file)

		def processor = new AccessWidenerJarProcessor("AccessWidener", true, localAccessWidenerProperty)
		specContext.modDependencies() >> []

		when:
		def spec = processor.buildSpec(specContext)

		then:
		spec != null
	}

	def "Dep AW"() {
		given:
		def specContext = Mock(SpecContext)

		def mod1 = Mock(FabricModJson.Mockable)
		mod1.getClassTweakers() >> ["test.accesswidener": ModEnvironment.UNIVERSAL]
		mod1.getId() >> "modid1"

		def mod2 = Mock(FabricModJson.Mockable)
		mod2.getClassTweakers() >> ["test2.accesswidener": ModEnvironment.UNIVERSAL]
		mod2.getId() >> "modid2"

		specContext.modDependencies() >> [mod1, mod2].shuffled()

		def processor = new AccessWidenerJarProcessor("AccessWidener", true, GradleTestUtil.mockRegularFileProperty(null))

		when:
		def spec = processor.buildSpec(specContext)

		then:
		spec != null
	}

	def "No AWs"() {
		given:
		def specContext = Mock(SpecContext)
		specContext.modDependencies() >> []

		def processor = new AccessWidenerJarProcessor("AccessWidener", true, GradleTestUtil.mockRegularFileProperty(null))

		when:
		def spec = processor.buildSpec(specContext)

		then:
		spec == null
	}
}
