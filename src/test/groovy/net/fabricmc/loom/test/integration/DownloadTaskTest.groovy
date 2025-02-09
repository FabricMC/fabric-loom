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

package net.fabricmc.loom.test.integration

import spock.lang.Unroll

import net.fabricmc.loom.test.unit.download.DownloadTest
import net.fabricmc.loom.test.util.GradleProjectTestTrait

import static net.fabricmc.loom.test.LoomTestConstants.STANDARD_TEST_VERSIONS
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class DownloadTaskTest extends DownloadTest implements GradleProjectTestTrait {
	@Unroll
	def "download (gradle #version)"() {
		setup:
		server.get("/simpleFile") {
			it.result("Hello World")
		}

		def gradle = gradleProject(project: "minimalBase", version: version)
		gradle.buildGradle << """
                dependencies {
                    minecraft "com.mojang:minecraft:1.21.4"
                    mappings "net.fabricmc:yarn:1.21.4+build.8:v2"
                }

                tasks.register("download", net.fabricmc.loom.task.DownloadTask) {
                    url = "${PATH}/simpleFile"
                    output = file("out.txt")
                }
            """
		when:
		def result = gradle.run(task: "download")
		def output = new File(gradle.projectDir, "out.txt")

		then:
		result.task(":download").outcome == SUCCESS
		output.text == "Hello World"

		where:
		version << STANDARD_TEST_VERSIONS
	}

	@Unroll
	def "download sha1 (gradle #version)"() {
		setup:
		server.get("/simpleFile") {
			it.result("Hello World")
		}

		def gradle = gradleProject(project: "minimalBase", version: version)
		gradle.buildGradle << """
                dependencies {
                    minecraft "com.mojang:minecraft:1.21.4"
                    mappings "net.fabricmc:yarn:1.21.4+build.8:v2"
                }

                tasks.register("download", net.fabricmc.loom.task.DownloadTask) {
                    url = "${PATH}/simpleFile"
                    sha1 = "0a4d55a8d778e5022fab701977c5d840bbc486d0"
                    output = file("out.txt")
                }
            """
		when:
		def result = gradle.run(task: "download")
		def output = new File(gradle.projectDir, "out.txt")

		then:
		result.task(":download").outcome == SUCCESS
		output.text == "Hello World"

		where:
		version << STANDARD_TEST_VERSIONS
	}

	@Unroll
	def "download max age (gradle #version)"() {
		setup:
		server.get("/simpleFile") {
			it.result("Hello World")
		}

		def gradle = gradleProject(project: "minimalBase", version: version)
		gradle.buildGradle << """
                dependencies {
                    minecraft "com.mojang:minecraft:1.21.4"
                    mappings "net.fabricmc:yarn:1.21.4+build.8:v2"
                }

                tasks.register("download", net.fabricmc.loom.task.DownloadTask) {
                    url = "${PATH}/simpleFile"
                    maxAge = Duration.ofDays(1)
                    output = file("out.txt")
                }
            """
		when:
		def result = gradle.run(task: "download")
		def output = new File(gradle.projectDir, "out.txt")

		then:
		result.task(":download").outcome == SUCCESS
		output.text == "Hello World"

		where:
		version << STANDARD_TEST_VERSIONS
	}
}
