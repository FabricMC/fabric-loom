/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2024 FabricMC
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
import static org.gradle.testkit.runner.TaskOutcome.FAILED

class ConfigurationCacheTest extends Specification implements GradleProjectTestTrait {
	@Unroll
	def "Configuration cache (task #task)"() {
		setup:
		def gradle = gradleProject(project: "minimalBase", version: PRE_RELEASE_GRADLE)
		gradle.buildGradle << """
            dependencies {
                minecraft 'com.mojang:minecraft:1.20.4'
                mappings 'net.fabricmc:yarn:1.20.4+build.3:v2'
                modImplementation 'net.fabricmc:fabric-loader:0.15.6'
                modImplementation 'net.fabricmc.fabric-api:fabric-api:0.95.4+1.20.4'
            }
            """.stripIndent()
		when:
		def result = gradle.run(task: task, isloatedProjects: false)
		def result2 = gradle.run(task: task, isloatedProjects: false)

		then:
		result.task(":${task}").outcome != FAILED
		result2.task(":${task}").outcome != FAILED

		where:
		task                      | _
		"help"                    | _
		"configureClientLaunch"   | _
		"jar"                     | _
		"check"                   | _
		"remapSourcesJar"         | _
		"build"                   | _
	}

	// Test GradleUtils.configurationInputFile invalidates the cache when the file changes
	@Unroll
	def "File input (#version)"() {
		setup:
		def gradle = gradleProject(project: "minimalBase", version: version)
		gradle.buildGradle << """
            dependencies {
                minecraft 'com.mojang:minecraft:1.20.4'
                mappings 'net.fabricmc:yarn:1.20.4+build.3:v2'
                modImplementation 'net.fabricmc:fabric-loader:0.15.6'
            }

			abstract class TestTask extends DefaultTask {
				@Input
				abstract Property<String> getModVersion()

				@TaskAction
				void run() {
					println "Version: " + modVersion.get()
				}
			}

			println "Configuring task testTask"
            tasks.register('testTask', TestTask) {
            	modVersion.set(loom.getModVersion()) // loom.getModVersion() returns a String
			}
            """.stripIndent()

		def fabricModJson = new File(gradle.projectDir, "src/main/resources/fabric.mod.json")
		fabricModJson.parentFile.mkdirs()
		fabricModJson.text = fmj("1.0.0")

		when:
		def result = gradle.run(task: "testTask")
		def result2 = gradle.run(task: "testTask")
		fabricModJson.text = fmj("2.0.0")
		def result3 = gradle.run(task: "testTask")

		then:
		// Test that the cache is created
		result.task(":testTask").outcome != FAILED
		result.output.contains("Calculating task graph as no cached configuration is available for tasks: testTask")
		result.output.contains("Configuring task testTask")
		result.output.contains("Version: 1.0.0")

		// Test that the cache is reused when nothing has changed
		result2.task(":testTask").outcome != FAILED
		!result2.output.contains("Calculating task graph")
		!result2.output.contains("Configuring task testTask")
		result2.output.contains("Version: 1.0.0")

		// Test that the cache is invalidated when the file changes
		result3.task(":testTask").outcome != FAILED
		result3.output.contains("Calculating task graph as configuration cache cannot be reused because file 'src/main/resources/fabric.mod.json' has changed.".replace("/", File.separator))
		result3.output.contains("Configuring task testTask")
		result3.output.contains("Version: 2.0.0")

		where:
		version << STANDARD_TEST_VERSIONS
	}

	static def fmj(String version) {
		return """
		{
			"schemaVersion": 1,
			"id": "test",
			"version": "${version}"
		}
		"""
	}
}
