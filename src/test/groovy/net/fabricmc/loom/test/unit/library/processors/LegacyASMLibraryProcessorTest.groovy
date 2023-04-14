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

import net.fabricmc.loom.configuration.providers.minecraft.library.LibraryProcessor
import net.fabricmc.loom.configuration.providers.minecraft.library.processors.LegacyASMLibraryProcessor
import net.fabricmc.loom.test.util.PlatformTestUtils

class LegacyASMLibraryProcessorTest extends LibraryProcessorTest {
	def "Removes legacy asm-all"() {
		when:
		def (original, context) = getLibs("1.4.7", PlatformTestUtils.MAC_OS_X64)
		def processor = new LegacyASMLibraryProcessor(PlatformTestUtils.MAC_OS_X64, context)
		def processed = mockLibraryProcessorManager().processLibraries([processor], original)

		then:
		processor.applicationResult == LibraryProcessor.ApplicationResult.MUST_APPLY

		hasLibrary("org.ow2.asm:asm-all", original)
		!hasLibrary("org.ow2.asm:asm-all", processed)
	}
}
