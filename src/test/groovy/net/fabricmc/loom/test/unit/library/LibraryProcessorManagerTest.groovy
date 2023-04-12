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

import net.fabricmc.loom.configuration.providers.minecraft.library.LibraryProcessorManager
import net.fabricmc.loom.test.util.MinecraftTestUtils
import net.fabricmc.loom.test.util.PlatformTestUtils
import net.fabricmc.loom.util.Platform

class LibraryProcessorManagerTest extends Specification {
	def "Windows x64"() {
		when:
		def platform = PlatformTestUtils.platform(Platform.OperatingSystem.WINDOWS, false)
		def libraryProcessor = new LibraryProcessorManager(platform)
		def meta = MinecraftTestUtils.getVersionMeta(id)
		def context = new TestLibraryContext(meta, false)

		def result = libraryProcessor.processLibraries(meta, context)

		then:
		result.dependencies().size() == dependencies
		result.libraries().size() > 0

		where:
		id       | dependencies
		"1.19.4" | 0
		"1.18.2" | 0
		"1.16.5" | 0
		"1.4.7"  | 0
	}

	def "Linux x64"() {
		when:
		def platform = PlatformTestUtils.platform(Platform.OperatingSystem.LINUX, false)
		def libraryProcessor = new LibraryProcessorManager(platform)
		def meta = MinecraftTestUtils.getVersionMeta(id)
		def context = new TestLibraryContext(meta, false)

		def result = libraryProcessor.processLibraries(meta, context)

		then:
		result.dependencies().size() == dependencies
		result.libraries().size() > 0

		where:
		id       | dependencies
		"1.19.4" | 0
		"1.18.2" | 0
		"1.16.5" | 0
		"1.4.7"  | 0
	}

	def "MacOS x64"() {
		when:
		def platform = PlatformTestUtils.platform(Platform.OperatingSystem.MAC_OS, false)
		def libraryProcessor = new LibraryProcessorManager(platform)
		def meta = MinecraftTestUtils.getVersionMeta(id)
		def context = new TestLibraryContext(meta, false)

		def result = libraryProcessor.processLibraries(meta, context)

		then:
		result.dependencies().size() == dependencies
		result.libraries().size() > 0

		where:
		id       | dependencies
		"1.19.4" | 0
		"1.18.2" | 17 // Arm64 support is added
		"1.16.5" | 17 // Arm64 support is added
		"1.4.7"  | 0
	}

	def "Windows arm64"() {
		when:
		def platform = PlatformTestUtils.platform(Platform.OperatingSystem.WINDOWS, true)
		def libraryProcessor = new LibraryProcessorManager(platform)
		def meta = MinecraftTestUtils.getVersionMeta(id)
		def context = new TestLibraryContext(meta, false)

		def result = libraryProcessor.processLibraries(meta, context)

		then:
		result.dependencies().size() == dependencies
		result.libraries().size() > 0

		where:
		id       | dependencies
		"1.19.4" | 7 // Arm64 support is added
		"1.18.2" | 0
		"1.16.5" | 0
		"1.4.7"  | 0
	}

	def "Linux arm64"() {
		when:
		def platform = PlatformTestUtils.platform(Platform.OperatingSystem.LINUX, true)
		def libraryProcessor = new LibraryProcessorManager(platform)
		def meta = MinecraftTestUtils.getVersionMeta(id)
		def context = new TestLibraryContext(meta, false)

		def result = libraryProcessor.processLibraries(meta, context)

		then:
		result.dependencies().size() == dependencies
		result.libraries().size() > 0

		where:
		id       | dependencies
		"1.19.4" | 7
		"1.18.2" | 0
		"1.16.5" | 0
		"1.4.7"  | 0
	}

	def "MacOS arm64"() {
		when:
		def platform = PlatformTestUtils.platform(Platform.OperatingSystem.MAC_OS, true)
		def libraryProcessor = new LibraryProcessorManager(platform)
		def meta = MinecraftTestUtils.getVersionMeta(id)
		def context = new TestLibraryContext(meta, false)

		def result = libraryProcessor.processLibraries(meta, context)

		then:
		result.dependencies().size() == dependencies
		result.libraries().size() > 0

		where:
		id       | dependencies
		"1.19.4" | 0
		"1.18.2" | 17
		"1.16.5" | 17
		"1.4.7"  | 0
	}
}
