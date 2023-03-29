/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022 FabricMC
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

import spock.lang.Specification

import net.fabricmc.loom.configuration.mods.JarSplitter

class JarSplitterTest extends Specification {
	public static final String SPLIT_INPUT_JAR_URL = "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-lifecycle-events-v1/2.1.0%2B33fbc738a9/fabric-lifecycle-events-v1-2.1.0%2B33fbc738a9.jar"
	public static final String CLIENT_INPUT_JAR_URL = "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-renderer-indigo/0.6.10%2B5187d39f95/fabric-renderer-indigo-0.6.10%2B5187d39f95.jar"
	public static final String COMMON_INPUT_JAR_URL = "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api-lookup-api-v1/1.6.7%2B9ff28f4095/fabric-api-lookup-api-v1-1.6.7%2B9ff28f4095.jar"
	public static final String NORMAL_INPUT_JAR_URL = "https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.14.8/fabric-loader-0.14.8.jar"

	public static final File workingDir = new File("build/test/split")

	def "analyse: split"() {
		given:
		def inputJar = downloadJarIfNotExists(SPLIT_INPUT_JAR_URL, "split.jar")
		when:
		def target = new JarSplitter(inputJar.toPath()).analyseTarget()
		then:
		target == JarSplitter.Target.SPLIT
	}

	def "analyse: client"() {
		given:
		def inputJar = downloadJarIfNotExists(CLIENT_INPUT_JAR_URL, "client.jar")
		when:
		def target = new JarSplitter(inputJar.toPath()).analyseTarget()
		then:
		target == JarSplitter.Target.CLIENT_ONLY
	}

	def "analyse: common"() {
		given:
		def inputJar = downloadJarIfNotExists(COMMON_INPUT_JAR_URL, "common.jar")
		when:
		def target = new JarSplitter(inputJar.toPath()).analyseTarget()
		then:
		target == JarSplitter.Target.COMMON_ONLY
	}

	def "analyse: normal"() {
		given:
		def inputJar = downloadJarIfNotExists(NORMAL_INPUT_JAR_URL, "normal.jar")
		when:
		def target = new JarSplitter(inputJar.toPath()).analyseTarget()
		then:
		target == null
	}

	def "split jar"() {
		given:
		def inputJar = downloadJarIfNotExists(SPLIT_INPUT_JAR_URL, "split.jar")
		def commonOutputJar = getFile("common-out.jar")
		def clientOutputJar = getFile("client-out.jar")

		def jarSplitter = new JarSplitter(inputJar.toPath())
		when:
		jarSplitter.split(commonOutputJar.toPath(), clientOutputJar.toPath())

		then:
		commonOutputJar.exists()
		clientOutputJar.exists()
	}

	File downloadJarIfNotExists(String url, String name) {
		File dst = new File(workingDir, name)

		if (!dst.exists()) {
			dst.parentFile.mkdirs()
			dst << new URL(url).newInputStream()
		}

		return dst
	}

	File getFile(String name) {
		File file = new File(workingDir, name)
		file.delete()
		return file
	}
}
