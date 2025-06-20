/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 FabricMC
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

import java.nio.file.Path

import spock.lang.TempDir

import net.fabricmc.loom.task.service.TinyRemapperServiceInterface
import net.fabricmc.loom.task.service.UnpickRemapperService
import net.fabricmc.loom.test.unit.service.mocks.MockTinyRemapper
import net.fabricmc.loom.test.unit.service.mocks.MockTinyRemapperService

import static org.mockito.Mockito.when

class UnpickRemapperServiceTest extends ServiceTestBase {
	@TempDir
	Path tempDir

	def "remap unpick"() {
		given:
		def tinyRemapperOptions = mockService(MockTinyRemapperService.TYPE)
		TinyRemapperServiceInterface tinyRemapperService = factory.get(tinyRemapperOptions)

		def mockTr = new MockTinyRemapper()
		when(tinyRemapperService.tinyRemapperForRemapping).thenReturn(mockTr.tinyRemapper)

		def inputFile = tempDir.resolve("input.unpick")
		inputFile.text = INPUT

		def options = UnpickRemapperService.TYPE.create(project) {
			it.tinyRemapper.set(tinyRemapperOptions)
		}

		UnpickRemapperService unpickRemapper = factory.get(options)

		when(mockTr.remapper.map("net.example.ExampleClass"))
				.thenReturn("com.remapped.NewClass")

		when(mockTr.remapper.mapFieldName("net.example.ExampleClass", "FIELD", null))
				.thenReturn("DLEIF")
		when:
		def remapped = unpickRemapper.remap(inputFile.toFile())

		then:
		remapped == """
			unpick v3

			group int Example
			\tcom.remapped.NewClass.DLEIF
			""".stripIndent().trim() + "\n"
	}

	// TODO move to a file
	String INPUT = """
unpick v3
group int Example
    net.example.ExampleClass.FIELD
""".trim()
}
