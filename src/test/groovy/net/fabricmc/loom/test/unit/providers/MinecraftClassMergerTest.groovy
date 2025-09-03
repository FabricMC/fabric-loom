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

package net.fabricmc.loom.test.unit.providers

import spock.lang.Specification

import net.fabricmc.loom.configuration.providers.minecraft.MinecraftClassMerger

import static org.objectweb.asm.Opcodes.ACC_FINAL
import static org.objectweb.asm.Opcodes.ACC_PRIVATE
import static org.objectweb.asm.Opcodes.ACC_PROTECTED
import static org.objectweb.asm.Opcodes.ACC_PUBLIC
import static org.objectweb.asm.Opcodes.ACC_STATIC

class MinecraftClassMergerTest extends Specification {
	// Defined here as we cannot use bitwise OR in the where block
	private static int ACC_PUBLIC_STATIC = ACC_PUBLIC | ACC_STATIC
	private static int ACC_PRIVATE_STATIC = ACC_PRIVATE | ACC_STATIC
	private static int ACC_PRIVATE_FINAL = ACC_PRIVATE | ACC_FINAL

	def "merge access"() {
		when:
		def merged = MinecraftClassMerger.mergeAccess(client, server)

		then:
		MinecraftClassMerger.formatMethodAccessFlags(merged) == MinecraftClassMerger.formatMethodAccessFlags(expected)

		where:
		client                    | server                     | expected
		ACC_PUBLIC                | ACC_PUBLIC                 | ACC_PUBLIC
		ACC_PRIVATE               | ACC_PUBLIC                 | ACC_PRIVATE
		ACC_PUBLIC                | ACC_PRIVATE                | ACC_PRIVATE
		ACC_PROTECTED             | ACC_PRIVATE                | ACC_PRIVATE
		ACC_PROTECTED             | ACC_PUBLIC                 | ACC_PROTECTED
		ACC_PUBLIC_STATIC         | ACC_PRIVATE_STATIC         | ACC_PRIVATE_STATIC
	}

	def "cannot merge access"() {
		when:
		MinecraftClassMerger.mergeAccess(client, server)

		then:
		thrown(IllegalStateException)

		where:
		client                    | server
		ACC_PRIVATE_STATIC        | ACC_PUBLIC
		ACC_PRIVATE               | ACC_PRIVATE_STATIC
		ACC_PRIVATE_FINAL         | ACC_PUBLIC
	}
}
