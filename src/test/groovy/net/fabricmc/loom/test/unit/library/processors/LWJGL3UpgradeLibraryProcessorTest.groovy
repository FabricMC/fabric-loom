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

package net.fabricmc.loom.test.unit.library.processors

import org.gradle.api.JavaVersion

import net.fabricmc.loom.configuration.providers.minecraft.library.Library
import net.fabricmc.loom.configuration.providers.minecraft.library.LibraryProcessor
import net.fabricmc.loom.configuration.providers.minecraft.library.processors.LWJGL3UpgradeLibraryProcessor
import net.fabricmc.loom.test.util.PlatformTestUtils

class LWJGL3UpgradeLibraryProcessorTest extends LibraryProcessorTest {
	def "Only apply to LWJGL3"() {
		when:
		def (_, context) = getLibs(id, PlatformTestUtils.WINDOWS_X64)
		def processor = new LWJGL3UpgradeLibraryProcessor(PlatformTestUtils.WINDOWS_X64, context)
		then:
		processor.applicationResult == result

		where:
		id       || result
		"1.19.2" || LibraryProcessor.ApplicationResult.CAN_APPLY
		"1.18.2" || LibraryProcessor.ApplicationResult.CAN_APPLY
		"1.17.1" || LibraryProcessor.ApplicationResult.CAN_APPLY
		"1.16.5" || LibraryProcessor.ApplicationResult.CAN_APPLY
		"1.15.2" || LibraryProcessor.ApplicationResult.CAN_APPLY
		"1.14.4" || LibraryProcessor.ApplicationResult.CAN_APPLY
		"1.12.2" || LibraryProcessor.ApplicationResult.DONT_APPLY // Not LWJGL 3
	}

	def "Apply when using Java 19 or later"() {
		when:
		def (_, context) = getLibs("1.19.4", PlatformTestUtils.WINDOWS_X64, version)
		def processor = new LWJGL3UpgradeLibraryProcessor(PlatformTestUtils.WINDOWS_X64, context)
		then:
		processor.applicationResult == result

		where:
		version                 || result
		JavaVersion.VERSION_20  || LibraryProcessor.ApplicationResult.MUST_APPLY
		JavaVersion.VERSION_19  || LibraryProcessor.ApplicationResult.MUST_APPLY
		JavaVersion.VERSION_17  || LibraryProcessor.ApplicationResult.CAN_APPLY
		JavaVersion.VERSION_1_8 || LibraryProcessor.ApplicationResult.CAN_APPLY
	}

	def "Dont apply when using Java 19 or later on supported LWJGL version"() {
		when:
		def (_, context) = getLibs("1.20.2", PlatformTestUtils.WINDOWS_X64, version)
		def processor = new LWJGL3UpgradeLibraryProcessor(PlatformTestUtils.WINDOWS_X64, context)
		then:
		processor.applicationResult == result

		where:
		version                 || result
		JavaVersion.VERSION_20  || LibraryProcessor.ApplicationResult.CAN_APPLY
		JavaVersion.VERSION_19  || LibraryProcessor.ApplicationResult.CAN_APPLY
		JavaVersion.VERSION_17  || LibraryProcessor.ApplicationResult.CAN_APPLY
		JavaVersion.VERSION_1_8 || LibraryProcessor.ApplicationResult.CAN_APPLY
	}

	def "Apply when adding macOS ARM64 support"() {
		when:
		def (_, context) = getLibs(id, PlatformTestUtils.MAC_OS_ARM64)
		def processor = new LWJGL3UpgradeLibraryProcessor(PlatformTestUtils.MAC_OS_ARM64, context)
		then:
		processor.applicationResult == result

		where:
		id       || result
		"1.19.2" || LibraryProcessor.ApplicationResult.CAN_APPLY
		"1.18.2" || LibraryProcessor.ApplicationResult.MUST_APPLY
		"1.17.1" || LibraryProcessor.ApplicationResult.MUST_APPLY
		"1.16.5" || LibraryProcessor.ApplicationResult.MUST_APPLY
		"1.15.2" || LibraryProcessor.ApplicationResult.MUST_APPLY
		"1.14.4" || LibraryProcessor.ApplicationResult.MUST_APPLY
		"1.12.2" || LibraryProcessor.ApplicationResult.DONT_APPLY // Not LWJGL 3
	}

	def "Apply when adding linux riscv support"() {
		when:
		def (_, context) = getLibs(id, PlatformTestUtils.LINUX_RISCV)
		def processor = new LWJGL3UpgradeLibraryProcessor(PlatformTestUtils.LINUX_RISCV, context)
		then:
		processor.applicationResult == result

		where:
		id       || result
		"1.21"   || LibraryProcessor.ApplicationResult.MUST_APPLY
		"1.19.4" || LibraryProcessor.ApplicationResult.MUST_APPLY
		"1.18.2" || LibraryProcessor.ApplicationResult.DONT_APPLY // Not using classpath natives.
		"1.16.5" || LibraryProcessor.ApplicationResult.DONT_APPLY
		"1.14.4" || LibraryProcessor.ApplicationResult.DONT_APPLY
		"1.12.2" || LibraryProcessor.ApplicationResult.DONT_APPLY // Not LWJGL 3
	}

	def "Upgrade LWJGL classpath natives"() {
		when:
		def (original, context) = getLibs("1.19.4", PlatformTestUtils.MAC_OS_X64, JavaVersion.VERSION_20)
		def processor = new LWJGL3UpgradeLibraryProcessor(PlatformTestUtils.MAC_OS_X64, context)
		def processed = mockLibraryProcessorManager().processLibraries([processor], original)

		then:
		// Test to make sure that we compile against the original version
		original.find { it.is("org.lwjgl:lwjgl-glfw") && it.target() == Library.Target.COMPILE }.version() == "3.3.1"
		processed.find { it.is("org.lwjgl:lwjgl-glfw") && it.target() == Library.Target.COMPILE }.version() == "3.3.1"
		// And at runtime we have the new version.
		processed.find { it.is("org.lwjgl:lwjgl-glfw") && it.target() == Library.Target.RUNTIME }.version() == "3.3.2"

		// Test to make sure that the natives were replaced.
		original.find { it.is("org.lwjgl:lwjgl-glfw") && it.target() == Library.Target.NATIVES }.version() == "3.3.1"
		processed.find { it.is("org.lwjgl:lwjgl-glfw") && it.target() == Library.Target.NATIVES }.version() == "3.3.2"
	}

	def "Upgrade LWJGL extracted natives"() {
		when:
		def (original, context) = getLibs("1.18.2", PlatformTestUtils.MAC_OS_X64, JavaVersion.VERSION_20)
		def processor = new LWJGL3UpgradeLibraryProcessor(PlatformTestUtils.MAC_OS_X64, context)
		def processed = mockLibraryProcessorManager().processLibraries([processor], original)

		then:
		// Test to make sure that we compile against the original version
		original.find { it.is("org.lwjgl:lwjgl-glfw") && it.target() == Library.Target.COMPILE }.version() == "3.2.1"
		processed.find { it.is("org.lwjgl:lwjgl-glfw") && it.target() == Library.Target.COMPILE }.version() == "3.2.1"
		// And at runtime we have the new version.
		processed.find { it.is("org.lwjgl:lwjgl-glfw") && it.target() == Library.Target.RUNTIME }.version() == "3.3.2"

		// Test to make sure that the natives were replaced.
		original.find { it.is("org.lwjgl:lwjgl-glfw") && it.target() == Library.Target.NATIVES }.version() == "3.2.1"
		processed.find { it.is("org.lwjgl:lwjgl-glfw") && it.target() == Library.Target.NATIVES }.version() == "3.3.2"
	}

	def "Upgrade LWJGL classpath natives Linux riscv"() {
		when:
		def (original, context) = getLibs("1.19.4", PlatformTestUtils.LINUX_RISCV, JavaVersion.VERSION_20)
		def processor = new LWJGL3UpgradeLibraryProcessor(PlatformTestUtils.LINUX_RISCV, context)
		def processed = mockLibraryProcessorManager().processLibraries([processor], original)

		then:
		// Test to make sure that we compile against the original version
		original.find { it.is("org.lwjgl:lwjgl-glfw") && it.target() == Library.Target.COMPILE }.version() == "3.3.1"
		processed.find { it.is("org.lwjgl:lwjgl-glfw") && it.target() == Library.Target.COMPILE }.version() == "3.3.1"
		// And at runtime we have the new version.
		processed.find { it.is("org.lwjgl:lwjgl-glfw") && it.target() == Library.Target.RUNTIME }.version() == "3.3.4"

		// Test to make sure that the natives were replaced.
		original.find { it.is("org.lwjgl:lwjgl-glfw") && it.target() == Library.Target.NATIVES }.version() == "3.3.1"
		processed.find { it.is("org.lwjgl:lwjgl-glfw") && it.target() == Library.Target.NATIVES }.version() == "3.3.4"
	}
}
