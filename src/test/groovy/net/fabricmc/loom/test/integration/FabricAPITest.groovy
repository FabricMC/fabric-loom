/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021-2023 FabricMC
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

import spock.lang.Specification
import spock.lang.Timeout
import spock.lang.Unroll

import net.fabricmc.loom.test.util.GradleProjectTestTrait
import net.fabricmc.loom.test.util.ServerRunner

import static net.fabricmc.loom.test.LoomTestConstants.*
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

@Timeout(value = 30, unit = TimeUnit.MINUTES)
class FabricAPITest extends Specification implements GradleProjectTestTrait {
	private static final String API_VERSION = "0.0.0+loom"

	@Unroll
	def "build and run (gradle #version, mixin ap disabled: #disableMixinAp)"() {
		setup:
		def gradle = gradleProject(
				repo: "https://github.com/FabricMC/fabric.git",
				commit: "f091af96c53963fadf9dbc391c67bb40e5678a96",
				version: version,
				patch: "fabric_api"
				)

		gradle.enableMultiProjectOptimisation()

		// Disable the mixin ap if needed. Fabric API is a large enough test project to see if something breaks.
		def mixinApPatch = ""

		if (disableMixinAp) {
			mixinApPatch = """

				allprojects {
					loom.mixin.useLegacyMixinAp = false
				}
				""".stripIndent()
		}

		// Set the version to something constant
		gradle.buildGradle.text = gradle.buildGradle.text.replace('project.version + "+" + (ENV.GITHUB_RUN_NUMBER ? "" : "local-") + getBranch()', "\"$API_VERSION\"") + mixinApPatch

		def server = ServerRunner.create(gradle.projectDir, "23w33a")
				.withMod(gradle.getOutputFile("fabric-api-${API_VERSION}.jar"))
		when:
		def result = gradle.run(tasks: [
			"build",
			"publishToMavenLocal"
		], args: [
			"--parallel",
			"-x",
			"check",
			"-x",
			"runDatagen",
			"-x",
			"runGametest"
		]) // Note: checkstyle does not appear to like being ran in a test runner
		gradle.printOutputFiles()

		def serverResult = server.run()
		then:
		result.task(":build").outcome == SUCCESS
		result.task(":prepareRemapJar").outcome == SUCCESS

		new File(gradle.mavenLocalDir, "net/fabricmc/fabric-api/fabric-biome-api-v1/13.0.11/fabric-biome-api-v1-13.0.11.jar").exists()
		new File(gradle.mavenLocalDir, "net/fabricmc/fabric-api/fabric-biome-api-v1/13.0.11/fabric-biome-api-v1-13.0.11-sources.jar").exists()

		serverResult.successful()
		serverResult.output.contains("- fabric-api $API_VERSION")
		where:
		[version, disableMixinAp] << [
			[DEFAULT_GRADLE],
			[false, true]
		].combinations()
	}
}
