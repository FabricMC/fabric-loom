/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2024 FabricMC
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

package net.fabricmc.loom.test.unit.service

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property

import net.fabricmc.loom.task.service.MappingsService
import net.fabricmc.loom.test.util.GradleTestUtil

class MappingsServiceTest extends ServiceTestBase {
	def "get mapping tree"() {
		given:
		MappingsService service = factory.get(new TestOptions(
				mappingsFile: GradleTestUtil.mockRegularFileProperty(new File("src/test/resources/mappings/PosInChunk.mappings")),
				from: GradleTestUtil.mockProperty("intermediary"),
				to: GradleTestUtil.mockProperty("named"),
				))

		when:
		def mappingTree = service.memoryMappingTree

		then:
		mappingTree.getClasses().size() == 2

		service.from == "intermediary"
		service.to == "named"
	}

	static class TestOptions implements MappingsService.Options {
		RegularFileProperty mappingsFile
		Property<String> from
		Property<String> to
		Property<Boolean> remapLocals = GradleTestUtil.mockProperty(false)
		Property<Boolean> AllowNoneExistent = GradleTestUtil.mockProperty(false)
		Property<String> serviceClass = serviceClassProperty(MappingsService.TYPE)
	}
}