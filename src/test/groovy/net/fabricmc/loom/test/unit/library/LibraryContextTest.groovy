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

import org.gradle.api.JavaVersion
import spock.lang.Specification

import net.fabricmc.loom.configuration.providers.minecraft.MinecraftVersionMeta
import net.fabricmc.loom.configuration.providers.minecraft.library.LibraryContext
import net.fabricmc.loom.test.util.MinecraftTestUtils
import net.fabricmc.loom.util.Platform

class LibraryContextTest extends Specification {
	def "Supports ARM64 macOS"() {
		when:
		def context = new LibraryContext(MinecraftTestUtils.getVersionMeta(id), JavaVersion.VERSION_17)

		then:
		context.supportsArm64(Platform.OperatingSystem.MAC_OS) == supported

		where:
		id       || supported
		"1.19.4" || true
		"1.18.2" || false
		"1.16.5" || false
		"1.4.7"  || false
	}

	def "Supports ARM64 windows"() {
		when:
		def context = new LibraryContext(MinecraftTestUtils.getVersionMeta(id), JavaVersion.VERSION_17)

		then:
		context.supportsArm64(Platform.OperatingSystem.WINDOWS) == supported

		where:
		id       || supported
		"23w16a" || true
		"1.19.4" || true
		"1.18.2" || false
		"1.16.5" || false
		"1.4.7"  || false
	}

	def "Uses LWJGL 3"() {
		when:
		def context = new LibraryContext(MinecraftTestUtils.getVersionMeta(id), JavaVersion.VERSION_17)

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
		def context = new LibraryContext(MinecraftTestUtils.getVersionMeta(id), JavaVersion.VERSION_17)

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
		def context = new LibraryContext(MinecraftTestUtils.getVersionMeta("1.19.4"), JavaVersion.VERSION_17)

		then:
		context.hasLibrary("commons-io:commons-io:2.11.0")
		!context.hasLibrary("net.fabricmc:fabric-loader")
	}

	def "Is runtime Java 19 or later"() {
		when:
		def context = new LibraryContext(MinecraftTestUtils.getVersionMeta("1.19.4"), javaVersion)

		then:
		context.isJava19OrLater() == isJava19OrLater

		where:
		javaVersion       	   || isJava19OrLater
		JavaVersion.VERSION_17 || false
		JavaVersion.VERSION_19 || true
		JavaVersion.VERSION_20 || true
	}

	def "Supports Java 19 or later"() {
		when:
		def metaJson = """
				{
				  "libraries": [
					{
					  "name": "org.lwjgl:lwjgl:${lwjglVersion}"
					}
				  ]
				}"""
		def context = new LibraryContext(MinecraftTestUtils.GSON.fromJson(metaJson, MinecraftVersionMeta.class), JavaVersion.VERSION_17)

		then:
		context.supportsJava19OrLater() == supportsJava19OrLater

		where:
		lwjglVersion || supportsJava19OrLater
		"2.1.2"      || false
		"3.0.0"      || false
		"3.0.5"      || false
		"3.1.5"      || false
		"3.3.1"      || false
		"3.3.2"      || true
		"3.3.3"      || true
		"3.4.0"      || true
		"4.0.0"      || true
	}
}
