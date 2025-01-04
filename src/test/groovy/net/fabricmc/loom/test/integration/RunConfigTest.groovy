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

import java.util.concurrent.TimeUnit

import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.Timeout
import spock.lang.Unroll
import spock.util.environment.RestoreSystemProperties

import net.fabricmc.loom.test.LoomTestConstants
import net.fabricmc.loom.test.util.GradleProjectTestTrait
import net.fabricmc.loom.util.download.Download

import static net.fabricmc.loom.test.LoomTestConstants.STANDARD_TEST_VERSIONS
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

// This test runs a mod that exits on mod init
class RunConfigTest extends Specification implements GradleProjectTestTrait {
	private static final List<String> tasks = [
		"runClient",
		"runServer",
		"runTestmodClient",
		"runTestmodServer",
		"runAutoTestServer"
	]
	private static final String TRACY_CAPTURE_LINUX = "https://github.com/modmuss50/tracy-utils/releases/download/0.0.2/linux-x86_64-tracy-capture"

	@Unroll
	def "Run config #task (gradle #version)"() {
		setup:
		def gradle = gradleProject(project: "runconfigs", sharedFiles: true, version: version)

		when:
		def result = gradle.run(task: task)

		then:
		result.task(":${task}").outcome == SUCCESS
		result.output.contains("This contains a space")

		where:
		version << STANDARD_TEST_VERSIONS * tasks.size()
		task << tasks * STANDARD_TEST_VERSIONS.size()
	}

	@Unroll
	def "Custom main class (gradle #version)"() {
		setup:
		def gradle = gradleProject(project: "runconfigs", sharedFiles: true, version: version)

		when:
		def result = gradle.run(task: 'runCustomMain')

		then:
		result.task(':runCustomMain').outcome == SUCCESS
		result.output.contains('hello custom main')

		where:
		version << STANDARD_TEST_VERSIONS
	}

	@RestoreSystemProperties
	@Unroll
	def "idea auto configuration (gradle #version)"() {
		setup:
		System.setProperty("idea.sync.active", "true")
		def gradle = gradleProject(project: "minimalBase", version: version)

		new File(gradle.projectDir, ".idea").mkdirs()

		gradle.buildGradle << '''
                dependencies {
                    minecraft "com.mojang:minecraft:1.18.1"
                    mappings "net.fabricmc:yarn:1.18.1+build.18:v2"
                    modImplementation "net.fabricmc:fabric-loader:0.12.12"
                }
            '''

		when:
		// Dont run with any tasks, the idea sync task should be invoked automatically due to the system prop
		def result = gradle.run(tasks: [])

		then:
		result.task(":ideaSyncTask").outcome == SUCCESS

		where:
		version << STANDARD_TEST_VERSIONS
	}

	// Test that the download assets task doesnt depend on a client run existing.
	@Unroll
	def "cleared runs (gradle #version)"() {
		setup:
		def gradle = gradleProject(project: "minimalBase", version: version)

		gradle.buildGradle << '''
                dependencies {
                    minecraft "com.mojang:minecraft:1.18.1"
                    mappings "net.fabricmc:yarn:1.18.1+build.18:v2"
                    modImplementation "net.fabricmc:fabric-loader:0.12.12"
                }

                loom {
    				runs.clear()
				}
            '''

		when:
		def result = gradle.run(tasks: ["downloadAssets"])

		then:
		result.task(":downloadAssets").outcome == SUCCESS

		where:
		version << STANDARD_TEST_VERSIONS
	}

	@Unroll
	def "prod server (gradle #version)"() {
		setup:
		def gradle = gradleProject(project: "minimalBase", version: version)
		gradle.buildGradle << '''
                dependencies {
                    minecraft "com.mojang:minecraft:1.21.4"
                    mappings "net.fabricmc:yarn:1.21.4+build.4:v2"
                    modImplementation "net.fabricmc:fabric-loader:0.16.9"
                }

                tasks.register("prodServer", net.fabricmc.loom.task.prod.ServerProductionRunTask) {
                    installerVersion = "1.0.1"
                }
            '''
		when:
		def result = gradle.run(task: "prodServer")

		then:
		result.task(":prodServer").outcome == SUCCESS

		where:
		version << STANDARD_TEST_VERSIONS
	}

	@Timeout(value = 10, unit = TimeUnit.MINUTES)
	@Unroll
	@IgnoreIf({ !os.linux }) // XVFB is installed on the CI for this test
	def "prod client (gradle #version)"() {
		setup:
		def tracyCapture = new File(LoomTestConstants.TEST_DIR, "tracy-capture")
		Download.create(TRACY_CAPTURE_LINUX).defaultCache().downloadPath(tracyCapture.toPath())

		def gradle = gradleProject(project: "minimalBase", version: version)
		gradle.buildGradle << '''
				configurations {
					productionMods
				}

                dependencies {
                    minecraft "com.mojang:minecraft:1.21.4"
                    mappings "net.fabricmc:yarn:1.21.4+build.4:v2"
                    modImplementation "net.fabricmc:fabric-loader:0.16.9"
                    modImplementation "net.fabricmc.fabric-api:fabric-api:0.114.0+1.21.4"

                    productionMods "net.fabricmc.fabric-api:fabric-api:0.114.0+1.21.4"
                }

                tasks.register("prodClient", net.fabricmc.loom.task.prod.ClientProductionRunTask) {
                	mods.from(configurations.productionMods)
                	jvmArgs.add("-Dfabric.client.gametest")

                	tracy {
                		tracyCapture = file("tracy-capture")
                		output = file("profile.tracy")
                	}
                }
            '''

		// Copy tracy into the project
		def projectTracyCapture = new File(gradle.projectDir, "tracy-capture")
		projectTracyCapture.bytes = tracyCapture.bytes
		projectTracyCapture.setExecutable(true)

		when:
		def result = gradle.run(task: "prodClient")

		then:
		result.task(":prodClient").outcome == SUCCESS
		new File(gradle.projectDir, "profile.tracy").exists()

		where:
		version << STANDARD_TEST_VERSIONS
	}
}
