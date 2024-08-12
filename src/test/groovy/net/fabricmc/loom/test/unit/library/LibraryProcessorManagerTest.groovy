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

import net.fabricmc.loom.configuration.providers.minecraft.library.Library
import net.fabricmc.loom.configuration.providers.minecraft.library.LibraryProcessorManager
import net.fabricmc.loom.configuration.providers.minecraft.library.processors.RuntimeLog4jLibraryProcessor
import net.fabricmc.loom.test.unit.library.processors.LibraryProcessorTest
import net.fabricmc.loom.test.util.GradleTestUtil
import net.fabricmc.loom.test.util.PlatformTestUtils

class LibraryProcessorManagerTest extends LibraryProcessorTest {
	// A test to ensure that we can add macOS ARM64 support on an unsupported version
	def "macOS arm64"() {
		when:
		def platform = PlatformTestUtils.MAC_OS_ARM64
		def (original, context) = getLibs("1.18.2", platform)
		def processed = new LibraryProcessorManager(platform, GradleTestUtil.mockRepositoryHandler()).processLibraries(original, context)

		then:
		// Test to make sure that we compile against the original version
		original.find { it.is("org.lwjgl:lwjgl-glfw") && it.target() == Library.Target.COMPILE }.version() == "3.2.1"
		processed.find { it.is("org.lwjgl:lwjgl-glfw") && it.target() == Library.Target.COMPILE }.version() == "3.2.1"
		// And at runtime we have the new version.
		processed.find { it.is("org.lwjgl:lwjgl-glfw") && it.target() == Library.Target.RUNTIME }.version() == "3.3.2"

		// Test to make sure that the natives were upgraded.
		original.find { it.is("org.lwjgl:lwjgl-glfw") && it.target() == Library.Target.NATIVES }.version() == "3.2.1"
		processed.find { it.is("org.lwjgl:lwjgl-glfw") && it.target() == Library.Target.NATIVES }.version() == "3.3.2"

		// Test to make sure that the natives were replaced.
		original.find { it.is("org.lwjgl:lwjgl-glfw") && it.target() == Library.Target.NATIVES }.classifier() == "natives-macos"
		processed.find { it.is("org.lwjgl:lwjgl-glfw") && it.target() == Library.Target.NATIVES }.classifier() == "natives-macos-arm64"
	}

	// Test to make sure that we dont upgrade LWJGL on an x64 mac
	def "macOS x64"() {
		when:
		def platform = PlatformTestUtils.MAC_OS_X64
		def (original, context) = getLibs("1.18.2", platform)
		def processed = new LibraryProcessorManager(platform, GradleTestUtil.mockRepositoryHandler()).processLibraries(original, context)

		then:
		// Test to make sure that we compile against the original version
		original.find { it.is("org.lwjgl:lwjgl-glfw") && it.target() == Library.Target.COMPILE }.version() == "3.2.1"
		processed.find { it.is("org.lwjgl:lwjgl-glfw") && it.target() == Library.Target.COMPILE }.version() == "3.2.1"
		// Make sure that there isn't a runtime library
		processed.find { it.is("org.lwjgl:lwjgl-glfw") && it.target() == Library.Target.RUNTIME } == null

		// Test to make sure that the natives were not upgraded.
		original.find { it.is("org.lwjgl:lwjgl-glfw") && it.target() == Library.Target.NATIVES }.version() == "3.2.1"
		processed.find { it.is("org.lwjgl:lwjgl-glfw") && it.target() == Library.Target.NATIVES }.version() == "3.2.1"

		// Test to make sure that the natives were not replaced.
		original.find { it.is("org.lwjgl:lwjgl-glfw") && it.target() == Library.Target.NATIVES }.classifier() == "natives-macos"
		processed.find { it.is("org.lwjgl:lwjgl-glfw") && it.target() == Library.Target.NATIVES }.classifier() == "natives-macos"
	}

	// A test to ensure that we can add linux RISC-V support on an unsupported version
	def "Linux riscv"() {
		when:
		def platform = PlatformTestUtils.LINUX_RISCV
		def (original, context) = getLibs("1.21", platform)
		def processed = new LibraryProcessorManager(platform, GradleTestUtil.mockRepositoryHandler()).processLibraries(original, context)

		then:
		// Test to make sure that we compile against the original version
		original.find { it.is("org.lwjgl:lwjgl-glfw") && it.target() == Library.Target.COMPILE }.version() == "3.3.3"
		processed.find { it.is("org.lwjgl:lwjgl-glfw") && it.target() == Library.Target.COMPILE }.version() == "3.3.3"
		// And at runtime we have the new version.
		processed.find { it.is("org.lwjgl:lwjgl-glfw") && it.target() == Library.Target.RUNTIME }.version() == "3.3.4"

		// Test to make sure that the natives were upgraded.
		original.find { it.is("org.lwjgl:lwjgl-glfw") && it.target() == Library.Target.NATIVES }.version() == "3.3.3"
		processed.find { it.is("org.lwjgl:lwjgl-glfw") && it.target() == Library.Target.NATIVES }.version() == "3.3.4"

		// Test to make sure that the natives were added.
		original.find { it.is("org.lwjgl:lwjgl-glfw") && it.target() == Library.Target.NATIVES }.classifier() == "natives-linux"
		processed.find { it.is("org.lwjgl:lwjgl-glfw") && it.target() == Library.Target.NATIVES }.classifier() == "natives-linux-riscv64"
	}

	def "runtime log4j"() {
		when:
		def platform = PlatformTestUtils.WINDOWS_X64
		def (original, context) = getLibs("1.19.2", platform)
		def processed = new LibraryProcessorManager(platform, GradleTestUtil.mockRepositoryHandler(), LibraryProcessorManager.DEFAULT_LIBRARY_PROCESSORS, [
			RuntimeLog4jLibraryProcessor.class.simpleName
		]).processLibraries(original, context)

		then:
		original.find { it.is("org.apache.logging.log4j") && it.target() == Library.Target.COMPILE } != null

		processed.find { it.is("org.apache.logging.log4j") && it.target() == Library.Target.RUNTIME } != null
		processed.find { it.is("org.apache.logging.log4j") && it.target() == Library.Target.COMPILE } == null
	}
}
