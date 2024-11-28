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

package net.fabricmc.loom.test.integration

import spock.lang.Specification
import spock.lang.Unroll

import net.fabricmc.loom.test.util.GradleProjectTestTrait

import static net.fabricmc.loom.test.LoomTestConstants.PRE_RELEASE_GRADLE
import static net.fabricmc.loom.test.LoomTestConstants.STANDARD_TEST_VERSIONS
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class DataGenerationTest extends Specification implements GradleProjectTestTrait {
	private static String DEPENDENCIES = """
		dependencies {
			minecraft "com.mojang:minecraft:1.20.2"
			mappings "net.fabricmc:yarn:1.20.2+build.4:v2"
			modImplementation "net.fabricmc:fabric-loader:0.14.23"
			modImplementation "net.fabricmc.fabric-api:fabric-api:0.90.0+1.20.2"
		}
	"""

	@Unroll
	def "dataGeneration (gradle #version)"() {
		setup:
		def gradle = gradleProject(project: "minimalBase", version: version)
		gradle.buildGradle << '''
                fabricApi {
                    configureDataGeneration()
                }
            ''' + DEPENDENCIES
		when:
		def result = gradle.run(task: "runDatagen")

		then:
		result.task(":runDatagen").outcome == SUCCESS

		where:
		version << STANDARD_TEST_VERSIONS
	}

	@Unroll
	def "dataGeneration sourceset (gradle #version)"() {
		setup:
		def gradle = gradleProject(project: "minimalBase", version: version)
		gradle.buildGradle << '''
                // Must configure the main mod
                loom.mods {
                    "example" {
                        sourceSet sourceSets.main
                    }
                }

                fabricApi {
                    configureDataGeneration {
                        createSourceSet = true
                        createRunConfiguration = true
                        modId = "example-datagen"
                        strictValidation = true
                    }
                }

                println("%%" + loom.runs.datagen.configName + "%%")
            ''' + DEPENDENCIES
		when:
		def result = gradle.run(task: "runDatagen")

		then:
		result.task(":runDatagen").outcome == SUCCESS
		result.output.contains("%%Data Generation%%")

		where:
		version << STANDARD_TEST_VERSIONS
	}

	@Unroll
	def "client dataGeneration (gradle #version)"() {
		setup:
		def gradle = gradleProject(project: "minimalBase", version: PRE_RELEASE_GRADLE)
		gradle.buildGradle << '''
                fabricApi {
                    configureDataGeneration {
                    	client = true
					}
                }
            ''' + DEPENDENCIES
		when:
		def result = gradle.run(task: "runDatagen")

		then:
		result.task(":runDatagen").outcome == SUCCESS
	}

	@Unroll
	def "client dataGeneration sourceset (gradle #version)"() {
		setup:
		def gradle = gradleProject(project: "minimalBase", version: PRE_RELEASE_GRADLE)
		gradle.buildGradle << '''
                // Must configure the main mod
                loom.mods {
                    "example" {
                        sourceSet sourceSets.main
                    }
                }

                fabricApi {
                    configureDataGeneration {
                        createSourceSet = true
                        createRunConfiguration = true
                        modId = "example-datagen"
                        strictValidation = true
                        client = true
                    }
                }
            ''' + DEPENDENCIES
		when:
		def result = gradle.run(task: "runDatagen")

		then:
		result.task(":runDatagen").outcome == SUCCESS
	}

	@Unroll
	def "split client dataGeneration (gradle #version)"() {
		setup:
		def gradle = gradleProject(project: "minimalBase", version: PRE_RELEASE_GRADLE)
		gradle.buildGradle << '''
				loom {
					splitEnvironmentSourceSets()
					mods {
						"example" {
							sourceSet sourceSets.main
							sourceSet sourceSets.client
						}
					}
                }

                fabricApi {
                    configureDataGeneration {
                    	client = true
					}
                }
            ''' + DEPENDENCIES
		when:
		def result = gradle.run(task: "runDatagen")

		then:
		result.task(":runDatagen").outcome == SUCCESS
	}

	@Unroll
	def "split client dataGeneration sourceset (gradle #version)"() {
		setup:
		def gradle = gradleProject(project: "minimalBase", version: PRE_RELEASE_GRADLE)
		gradle.buildGradle << '''
                loom {
					splitEnvironmentSourceSets()
					mods {
						"example" {
							sourceSet sourceSets.main
							sourceSet sourceSets.client
						}
					}
                }

                fabricApi {
                    configureDataGeneration {
                        createSourceSet = true
                        createRunConfiguration = true
                        modId = "example-datagen"
                        strictValidation = true
                        client = true
                    }
                }
            ''' + DEPENDENCIES
		when:
		def result = gradle.run(task: "runDatagen")

		then:
		result.task(":runDatagen").outcome == SUCCESS
	}
}
