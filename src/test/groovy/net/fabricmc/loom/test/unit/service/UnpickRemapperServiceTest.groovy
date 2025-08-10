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

import java.nio.file.Files
import java.nio.file.Path

import groovy.transform.Immutable
import spock.lang.TempDir

import net.fabricmc.loom.task.service.TinyRemapperServiceInterface
import net.fabricmc.loom.task.service.UnpickRemapperService
import net.fabricmc.loom.test.unit.service.mocks.MockTinyRemapper
import net.fabricmc.loom.test.unit.service.mocks.MockTinyRemapperService
import net.fabricmc.loom.util.Pair
import net.fabricmc.loom.util.ZipUtils
import net.fabricmc.tinyremapper.api.TrClass
import net.fabricmc.tinyremapper.api.TrField

import static org.mockito.Mockito.mock
import static org.mockito.Mockito.when

// Based on https://github.com/Earthcomputer/unpick-v3-parser/blob/68b11c50a7c97a75218f70f5ec1291a38b178ad7/src/test/java/net/earthcomputer/unpickv3parser/remapper/TestRemapper.java
class UnpickRemapperServiceTest extends ServiceTestBase {
	private static final Map<String, List<String>> PACKAGES = [
		"unmapped.foo": [
			"unmapped.foo.A",
			"unmapped.foo.B"
		],
		"unmapped.bar": ["unmapped.bar.C"]
	]

	private static final Map<String, String> CLASSES = [
		"unmapped/foo/A": "mapped/foo/X",
		"unmapped/foo/B": "mapped/bar/Y",
		"unmapped/bar/C": "mapped/bar/Z"
	]

	private static final Map<MemberKey, String> FIELDS = [
		(new MemberKey("unmapped/foo/B", "baz", "I")): "quux"
	]

	private static final Map<MemberKey, String> METHODS = [
		(new MemberKey("unmapped/foo/B", "foo2", "(Lunmapped/foo/A;)V")): "bar2"
	]

	@TempDir
	Path tempDir

	def "remap unpick"() {
		given:
		def classpath = zip(PACKAGES.values().flatten())

		def tinyRemapperOptions = mockService(MockTinyRemapperService.TYPE)
		tinyRemapperOptions.classpath.from(classpath)
		TinyRemapperServiceInterface tinyRemapperService = factory.get(tinyRemapperOptions)

		def mockTr = new MockTinyRemapper()
		when(tinyRemapperService.tinyRemapperForRemapping).thenReturn(mockTr.tinyRemapper)

		def inputFile = tempDir.resolve("input.unpick")
		inputFile.text = testFile("input")

		def options = UnpickRemapperService.TYPE.create(project) {
			it.tinyRemapper.set(tinyRemapperOptions)
		}

		UnpickRemapperService unpickRemapper = factory.get(options)

		CLASSES.each { unmapped, mapped ->
			when(mockTr.remapper.map(unmapped)).thenReturn(mapped)
		}

		FIELDS.each { key, mapped ->
			when(mockTr.remapper.mapFieldName(key.owner, key.name, key.descriptor)).thenReturn(mapped)
		}

		FIELDS.groupBy { it.key.owner }.each { owner, fields ->
			def mockClass = mock(TrClass.class)
			when(mockTr.trEnvironment.getClass(owner)).thenReturn(mockClass)
			def mockFields = fields.collect { key, mapped ->
				def mockField = mock(TrField.class)
				when(mockField.name).thenReturn(key.name)
				when(mockField.desc).thenReturn(key.descriptor)
				mockField
			}
			when(mockClass.fields).thenReturn(mockFields)
		}

		METHODS.each { key, mapped ->
			when(mockTr.remapper.mapMethodName(key.owner, key.name, key.descriptor)).thenReturn(mapped)
		}

		when:
		def remapped = unpickRemapper.remap(inputFile.toFile())

		then:
		remapped == testFile("remapped")
	}

	// Load the given name from resources
	private static String testFile(String name) {
		return UnpickRemapperServiceTest.class.getResource("/unpick/${name}.unpick").text
	}

	private Path zip(List<String> entries) {
		def zip = Files.createTempFile(tempDir, "loom", ".zip")
		Files.delete(zip)

		def files = entries.stream().map {
			new Pair<>(it.replace(".", "/") + ".class", new byte[0])
		}.toList()

		ZipUtils.add(zip, files)
		return zip
	}

	@Immutable
	static class MemberKey {
		final String owner
		final String name
		final String descriptor
	}
}
