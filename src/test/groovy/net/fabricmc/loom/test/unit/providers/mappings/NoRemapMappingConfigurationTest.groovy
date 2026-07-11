/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2026 FabricMC
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

package net.fabricmc.loom.test.unit.providers.mappings

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.nio.file.Files
import java.nio.file.Path

import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

import net.fabricmc.loom.configuration.providers.mappings.NoRemapMappingConfiguration

class NoRemapMappingConfigurationTest extends Specification {
	private static final Method VALIDATE_MAPPINGS = NoRemapMappingConfiguration.getDeclaredMethod("validateMappings", Path)

	static {
		VALIDATE_MAPPINGS.setAccessible(true)
	}

	@TempDir
	Path tempDir

	def "accepts official-only mappings"() {
		given:
		Path mappings = writeMappings("tiny\t2\t0\tofficial\n")

		when:
		validateMappings(mappings)

		then:
		noExceptionThrown()
	}

	@Unroll
	def "rejects mappings with #description"() {
		given:
		Path mappings = writeMappings(header)

		when:
		validateMappings(mappings)

		then:
		def exception = thrown(IOException)
		exception.message == "Annotations mappings must contain only the official namespace"

		where:
		description             | header
		"a non-official source" | "tiny\t2\t0\tnamed\n"
		"a destination namespace" | "tiny\t2\t0\tofficial\tnamed\n"
	}

	private Path writeMappings(String mappings) {
		return Files.writeString(tempDir.resolve("mappings.tiny"), mappings)
	}

	private static void validateMappings(Path mappings) {
		try {
			VALIDATE_MAPPINGS.invoke(null, mappings)
		} catch (InvocationTargetException e) {
			throw e.cause
		}
	}
}
