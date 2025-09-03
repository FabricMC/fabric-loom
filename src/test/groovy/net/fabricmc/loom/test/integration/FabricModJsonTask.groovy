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

import spock.lang.Specification
import spock.lang.Unroll

import net.fabricmc.loom.test.util.GradleProjectTestTrait

import static net.fabricmc.loom.test.LoomTestConstants.STANDARD_TEST_VERSIONS
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class FabricModJsonTask extends Specification implements GradleProjectTestTrait {
	@Unroll
	def "Generate FMJ"() {
		setup:
		def gradle = gradleProject(project: "minimalBase", version: version)

		gradle.buildGradle << '''
			dependencies {
				minecraft "com.mojang:minecraft:1.21.8"
				mappings "net.fabricmc:yarn:1.21.8+build.1:v2"
			}

			tasks.register("generateModJson", net.fabricmc.loom.task.FabricModJsonV1Task) {
				outputFile = file("fabric.mod.json")

				json {
					modId = "examplemod"
					version = "1.0.0"
				}
			}
		'''

		when:
		// Run the task twice to ensure its up to date
		def result = gradle.run(task: "generateModJson")

		then:
		result.task(":generateModJson").outcome == SUCCESS
		new File(gradle.projectDir, "fabric.mod.json").text == """
		{
		  "schemaVersion": 1,
		  "id": "examplemod",
		  "version": "1.0.0"
		}
		""".stripIndent().trim()

		where:
		version << STANDARD_TEST_VERSIONS
	}
}
