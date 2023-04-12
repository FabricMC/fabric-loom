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

package net.fabricmc.loom.test.unit.library

import spock.lang.Specification

import net.fabricmc.loom.configuration.providers.minecraft.library.LibraryContext
import net.fabricmc.loom.test.util.MinecraftTestUtils

class LibraryContextTest extends Specification {
	def "Supports ARM64 macOS"() {
		when:
		def context = new LibraryContext(MinecraftTestUtils.getVersionMeta(id), javaVersion)

		then:
		context.supportsArm64MacOS() == supported

		where:
		id       || supported
		"1.19.4" || true
		"1.18.2" || false
		"1.16.5" || false
		"1.4.7"  || false
	}

	def "Uses LWJGL 3"() {
		when:
		def context = new LibraryContext(MinecraftTestUtils.getVersionMeta(id), javaVersion)

		then:
		context.usesLWJGL3() == lwjgl3

		where:
		id       || lwjgl3
		"1.19.4" || true
		"1.18.2" || true
		"1.16.5" || true
		"1.12.2" || false
		"1.8.9"  || false
		"1.4.7"  || false
	}

	def "Has classpath natives"() {
		when:
		def context = new LibraryContext(MinecraftTestUtils.getVersionMeta(id), javaVersion)

		then:
		context.hasClasspathNatives() == hasClasspathNatives

		where:
		id       || hasClasspathNatives
		"1.19.4" || true
		"1.18.2" || false
		"1.16.5" || false
		"1.12.2" || false
		"1.8.9"  || false
		"1.4.7"  || false
	}

	def "Has library"() {
		when:
		def context = new LibraryContext(MinecraftTestUtils.getVersionMeta("1.19.4"), javaVersion)

		then:
		context.hasLibrary("commons-io:commons-io:2.11.0")
		!context.hasLibrary("net.fabricmc:fabric-loader")
	}
}
