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

package net.fabricmc.loom.test.unit

import java.nio.file.Path

import org.intellij.lang.annotations.Language
import spock.lang.Specification

import net.fabricmc.loom.configuration.sandbox.SandboxMetadata
import net.fabricmc.loom.test.util.PlatformTestUtils
import net.fabricmc.loom.test.util.ZipTestUtils

class SandboxMetadataTest extends Specification {
	def "test sandbox metadata"() {
		given:
		def sandboxJar = createSandboxJar("""
			{
				"version": 1,
				"mainClass": "net.fabricmc.loom.test.Main",
				"platforms": {
					"windows": [
						"arm64",
						"x86_64"
					],
					"macos": [
						"arm64"
					]
				}
			}
			""")

		when:
		def metadata = SandboxMetadata.readFromJar(sandboxJar)

		then:
		metadata.mainClass() == "net.fabricmc.loom.test.Main"

		metadata.supportsPlatform(PlatformTestUtils.WINDOWS_X64)
		metadata.supportsPlatform(PlatformTestUtils.WINDOWS_ARM64)

		!metadata.supportsPlatform(PlatformTestUtils.LINUX_X64)
		!metadata.supportsPlatform(PlatformTestUtils.LINUX_ARM64)

		!metadata.supportsPlatform(PlatformTestUtils.MAC_OS_X64)
		metadata.supportsPlatform(PlatformTestUtils.MAC_OS_ARM64)
	}

	private static Path createSandboxJar(@Language("json") String json) {
		return ZipTestUtils.createZip(["fabric-sandbox.json": json])
	}
}
