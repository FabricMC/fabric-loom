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
import net.fabricmc.loom.configuration.processors.MinecraftJarProcessorManager
import net.fabricmc.loom.test.util.processor.TestMinecraftJarProcessor

class MinecraftJarProcessorManagerTest extends Specification {
	def "Cache value matches"() {
		when:
		def specContext = Mock(SpecContext)

		def processor1 = new TestMinecraftJarProcessor(input: "Test1")
		def processor2 = new TestMinecraftJarProcessor(input: "Test2")
		def manager1 = MinecraftJarProcessorManager.create([processor1, processor2], specContext)
		def manager2 = MinecraftJarProcessorManager.create([processor1, processor2], specContext)

		then:
		manager1.jarHash == manager2.jarHash
		manager1.jarHash == "eb6faafa72"
	}

	def "Cache value does not match"() {
		when:
		def specContext = Mock(SpecContext)

		def processor1 = new TestMinecraftJarProcessor(input: "Test1")
		def processor2 = new TestMinecraftJarProcessor(input: "Test2")
		def manager1 = MinecraftJarProcessorManager.create([processor1], specContext)
		def manager2 = MinecraftJarProcessorManager.create([processor1, processor2], specContext)

		then:
		manager1.jarHash != manager2.jarHash
		manager1.jarHash == "a714eb2de6"
		manager2.jarHash == "eb6faafa72"
	}
}
