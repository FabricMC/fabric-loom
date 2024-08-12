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

package net.fabricmc.loom.test.unit.library.processors

import net.fabricmc.loom.configuration.providers.minecraft.library.Library
import net.fabricmc.loom.configuration.providers.minecraft.library.LibraryProcessor
import net.fabricmc.loom.configuration.providers.minecraft.library.processors.RiscVNativesLibraryProcessor
import net.fabricmc.loom.test.util.PlatformTestUtils

class RiscVNativesLibraryProcessorTest extends LibraryProcessorTest {
	def "Apply when adding linux riscv support"() {
		when:
		def (_, context) = getLibs(id, PlatformTestUtils.LINUX_RISCV)
		def processor = new RiscVNativesLibraryProcessor(PlatformTestUtils.LINUX_RISCV, context)
		then:
		processor.applicationResult == result

		where:
		id       || result
		"1.21" || LibraryProcessor.ApplicationResult.MUST_APPLY
		"1.20.1" || LibraryProcessor.ApplicationResult.MUST_APPLY
		"1.14.4" || LibraryProcessor.ApplicationResult.DONT_APPLY // Not using classpath natives
		"1.12.2" || LibraryProcessor.ApplicationResult.DONT_APPLY // Not LWJGL 3
	}

	def "Never apply on none riscv platforms"() {
		when:
		def (_, context) = getLibs(id, platform)
		def processor = new RiscVNativesLibraryProcessor(platform, context)
		then:
		processor.applicationResult == LibraryProcessor.ApplicationResult.DONT_APPLY

		where:
		id       | platform
		"1.21" | PlatformTestUtils.LINUX_ARM64
		"1.21" | PlatformTestUtils.LINUX_X64
		"1.19.4" | PlatformTestUtils.MAC_OS_X64
		"1.18.2" | PlatformTestUtils.WINDOWS_X64
		"1.17.1" | PlatformTestUtils.MAC_OS_X64
		"1.16.5" | PlatformTestUtils.MAC_OS_X64
		"1.15.2" | PlatformTestUtils.LINUX_X64
		"1.14.4" | PlatformTestUtils.MAC_OS_X64
		"1.12.2" | PlatformTestUtils.WINDOWS_X64
	}

	def "Add linux riscv natives"() {
		when:
		def (original, context) = getLibs("1.21", PlatformTestUtils.LINUX_RISCV)
		def processor = new RiscVNativesLibraryProcessor(PlatformTestUtils.LINUX_RISCV, context)
		def processed = mockLibraryProcessorManager().processLibraries([processor], original)

		then:
		// Test that the riscv64 natives are added alongside the existing ones
		def originalNatives = original.findAll { it.is("org.lwjgl") && it.target() == Library.Target.NATIVES }
		originalNatives.count { it.classifier() == "natives-linux-riscv64" } == 0
		originalNatives.count { it.classifier() == "natives-linux" } > 0

		def processedNatives = processed.findAll { it.is("org.lwjgl") && it.target() == Library.Target.NATIVES }
		processedNatives.count { it.classifier() == "natives-linux-riscv64" } > 0
		processedNatives.count { it.classifier() == "natives-linux" } > 0
	}
}
