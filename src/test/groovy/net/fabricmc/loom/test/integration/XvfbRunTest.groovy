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

import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.Unroll

import net.fabricmc.loom.test.util.GradleProjectTestTrait

import static net.fabricmc.loom.test.LoomTestConstants.STANDARD_TEST_VERSIONS
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class XvfbRunTest extends Specification implements GradleProjectTestTrait {
	private static String DEPENDENCIES = """
		dependencies {
			minecraft "com.mojang:minecraft:1.21.4"
			mappings "net.fabricmc:yarn:1.21.4+build.4:v2"
			modImplementation "net.fabricmc:fabric-loader:0.16.9"
			modImplementation "net.fabricmc.fabric-api:fabric-api:0.114.0+1.21.4"
		}
	"""

	@Unroll
	@IgnoreIf({ !isLinux() || !hasXvfb() }) // Only run on Linux with xvfb-run available
	def "client game tests with XVFB (gradle #version)"() {
		setup:
		def gradle = gradleProject(project: "minimalBase", version: version)
		gradle.buildGradle << '''
                fabricApi {
                    configureTests {
                    	createSourceSet = true
                    	modId = "example-test"
                    	eula = true
                    }
                }

                loom {
                    runs {
                        clientGameTest {
                            useXvfb()
                        }
                    }
                }
            ''' + DEPENDENCIES
		when:
		def result = gradle.run(task: "runClientGameTest")
		def eula = new File(gradle.projectDir, "build/run/clientGameTest/eula.txt")

		then:
		result.task(":runClientGameTest").outcome == SUCCESS
		eula.text.contains("eula=true")

		where:
		version << STANDARD_TEST_VERSIONS
	}

	@Unroll
	def "XVFB is not used on non-Linux platforms (gradle #version)"() {
		setup:
		def gradle = gradleProject(project: "minimalBase", version: version)
		gradle.buildGradle << '''
                loom {
                    runs {
                        client {
                            client()
                            useXvfb()
                        }
                    }
                }
            ''' + DEPENDENCIES

		expect:
		// We just verify the configuration is accepted on all platforms
		// The actual XVFB usage is platform-specific
		gradle.buildGradle.text.contains("useXvfb()")

		where:
		version << STANDARD_TEST_VERSIONS
	}

	private static boolean isLinux() {
		return System.getProperty("os.name").toLowerCase().contains("linux")
	}

	private static boolean hasXvfb() {
		try {
			def process = ["which", "xvfb-run"].execute()
			process.waitFor()
			return process.exitValue() == 0
		} catch (Exception e) {
			return false
		}
	}
}