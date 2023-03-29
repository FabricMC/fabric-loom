/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021-2022 FabricMC
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

import spock.lang.Specification
import spock.lang.Unroll

import net.fabricmc.loom.test.util.GradleProjectTestTrait

import static net.fabricmc.loom.test.LoomTestConstants.STANDARD_TEST_VERSIONS
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class MCJarConfigTest extends Specification implements GradleProjectTestTrait {
	@Unroll
	def "server only (gradle #version)"() {
		setup:
		def gradle = gradleProject(project: "minimalBase", version: version)

		gradle.buildGradle << '''
                loom {
                    serverOnlyMinecraftJar()
                }

                dependencies {
                    minecraft "com.mojang:minecraft:1.18.1"
                    mappings "net.fabricmc:yarn:1.18.1+build.18:v2"
                    modImplementation "net.fabricmc:fabric-loader:0.12.12"
                }
            '''

		when:
		def result = gradle.run(tasks: ["build", "ideaSyncTask"])

		then:
		result.task(":build").outcome == SUCCESS

		where:
		version << STANDARD_TEST_VERSIONS
	}

	@Unroll
	def "client only (gradle #version)"() {
		setup:
		def gradle = gradleProject(project: "minimalBase", version: version)

		gradle.buildGradle << '''
                loom {
                    clientOnlyMinecraftJar()
                }

                dependencies {
                    minecraft "com.mojang:minecraft:1.18.1"
                    mappings "net.fabricmc:yarn:1.18.1+build.18:v2"
                    modImplementation "net.fabricmc:fabric-loader:0.12.12"
                }
            '''

		when:
		def result = gradle.run(tasks: ["build", "ideaSyncTask"])

		then:
		result.task(":build").outcome == SUCCESS

		where:
		version << STANDARD_TEST_VERSIONS
	}

	@Unroll
	def "split (gradle #version)"() {
		setup:
		def gradle = gradleProject(project: "minimalBase", version: version)

		gradle.buildGradle << '''
                loom {
                    splitMinecraftJar()
                }

                dependencies {
                    minecraft "com.mojang:minecraft:1.18.1"
                    mappings "net.fabricmc:yarn:1.18.1+build.18:v2"
                    modImplementation "net.fabricmc:fabric-loader:0.12.12"
                }
            '''

		when:
		def result = gradle.run(tasks: ["build", "ideaSyncTask"])

		then:
		result.task(":build").outcome == SUCCESS

		where:
		version << STANDARD_TEST_VERSIONS
	}

	@Unroll
	def "split env (gradle #version)"() {
		setup:
		def gradle = gradleProject(project: "minimalBase", version: version)

		gradle.buildGradle << '''
                loom {
                    splitEnvironmentSourceSets()
                }

                dependencies {
                    minecraft "com.mojang:minecraft:1.18.1"
                    mappings "net.fabricmc:yarn:1.18.1+build.18:v2"
                    modImplementation "net.fabricmc:fabric-loader:0.12.12"
                }
            '''

		when:
		def result = gradle.run(tasks: ["build", "ideaSyncTask"])

		then:
		result.task(":build").outcome == SUCCESS

		where:
		version << STANDARD_TEST_VERSIONS
	}
}
