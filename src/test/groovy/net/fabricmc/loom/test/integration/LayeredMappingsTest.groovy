/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2018-2025 FabricMC
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
import net.fabricmc.loom.util.Constants

import static net.fabricmc.loom.test.LoomTestConstants.DEFAULT_GRADLE
import static net.fabricmc.loom.test.LoomTestConstants.STANDARD_TEST_VERSIONS
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class LayeredMappingsTest extends Specification implements GradleProjectTestTrait {
	def "build #layer"() {
		setup:
		def gradle = gradleProject(project: "minimalBase", version: DEFAULT_GRADLE)
		gradle.buildGradle << """
            repositories {
                maven {
                    name = 'ParchmentMC'
                    url = 'https://maven.parchmentmc.org'
                }
            }
            dependencies {
                minecraft "com.mojang:minecraft:1.21.4"
                mappings loom.layered {
                    $layer
                }
            }
        """

		if (layer.contains("// Drop none roots")) {
			new File(gradle.projectDir, "gradle.properties").text = "${Constants.Properties.DROP_NON_INTERMEDIATE_ROOT_METHODS}=true"
		}

		when:
		def result = gradle.run(task: "build")

		then:
		result.task(":build").outcome == SUCCESS

		where:
		layer << [
			// Only mojang mappings
			"""
                officialMojangMappings()
            """,
			// Yarn on top of Mojmap
			"""
				// Drop none roots
                officialMojangMappings()
                mappings("net.fabricmc:yarn:1.21.4+build.8:v2")
            """,
			// Mojmap on top of yarn
			"""
                mappings("net.fabricmc:yarn:1.21.4+build.8:v2")
                officialMojangMappings()
            """,
			//  Mojmap with parchment
			"""
                officialMojangMappings()
                parchment("org.parchmentmc.data:parchment-1.21.4:2025.01.19@zip")
            """,
			// Yarn on top of Mojmap with parchment
			"""
				// Drop none roots
                officialMojangMappings()
                parchment("org.parchmentmc.data:parchment-1.21.4:2025.01.19@zip")
                mappings("net.fabricmc:yarn:1.21.4+build.8:v2")
            """,
		]
	}

	@Unroll
	def "build (gradle #version)"() {
		setup:
		def gradle = gradleProject(project: "mojangMappings", version: version)

		when:
		def result = gradle.run(task: "build")
		def dependenciesResult = gradle.run(task: "dependencies")

		then:
		result.task(":build").outcome == SUCCESS
		dependenciesResult.task(":dependencies").outcome == SUCCESS

		where:
		version << STANDARD_TEST_VERSIONS
	}

	@Unroll
	def "build no intermediary (gradle #version)"() {
		setup:
		def gradle = gradleProject(project: "mojangMappings", version: version)
		gradle.buildGradle << '''
			loom {
				noIntermediateMappings()
			}
		'''

		when:
		def result = gradle.run(task: "build")

		then:
		result.task(":build").outcome == SUCCESS

		where:
		version << STANDARD_TEST_VERSIONS
	}

	@Unroll
	def "mojang mappings without synthetic field names (gradle #version)"() {
		setup:
		def gradle = gradleProject(project: "minimalBase", version: version)

		gradle.buildGradle << '''
                dependencies {
                    minecraft "com.mojang:minecraft:1.18-pre5"
                    mappings loom.layered {
						officialMojangMappings {
							nameSyntheticMembers = false
						}
					}
                }
            '''

		when:
		def result = gradle.run(task: "build")

		then:
		result.task(":build").outcome == SUCCESS

		where:
		version << STANDARD_TEST_VERSIONS
	}

	@Unroll
	def "fail with wrong officialMojangMappings usage (gradle #version)"() {
		setup:
		def gradle = gradleProject(project: "minimalBase", version: version)

		gradle.buildGradle << '''
				dependencies {
					minecraft "com.mojang:minecraft:1.18.2"
					mappings loom.layered {
						// This is the wrong method to call!
						loom.officialMojangMappings()
					}
				}
			'''

		when:
		def result = gradle.run(task: "build", expectFailure: true)

		then:
		result.output.contains("Use `officialMojangMappings()` when configuring layered mappings, not the extension method `loom.officialMojangMappings()`")

		where:
		version << STANDARD_TEST_VERSIONS
	}

	@Unroll
	def "mojang mappings via lazy provider (gradle #version)"() {
		setup:
		def gradle = gradleProject(project: "minimalBase", version: version)

		gradle.buildGradle << '''
                dependencies {
                    minecraft "com.mojang:minecraft:1.18-pre5"
                    mappings project.provider {
						loom.layered() {
							officialMojangMappings()
						}
					}
                }
            '''

		when:
		def result = gradle.run(task: "build")

		then:
		result.task(":build").outcome == SUCCESS

		where:
		version << STANDARD_TEST_VERSIONS
	}

	@Unroll
	def "parchment #version"() {
		setup:
		def gradle = gradleProject(project: "parchment", version: version)

		when:
		def result = gradle.run(task: "build")

		then:
		result.task(":build").outcome == SUCCESS

		where:
		version << STANDARD_TEST_VERSIONS
	}
}
