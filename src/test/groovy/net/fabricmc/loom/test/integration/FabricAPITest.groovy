/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021 FabricMC
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

import net.fabricmc.loom.test.util.GradleProjectTestTrait
import net.fabricmc.loom.test.util.ServerRunner
import spock.lang.Specification
import spock.lang.Timeout
import spock.lang.Unroll

import java.util.concurrent.TimeUnit

import static net.fabricmc.loom.test.LoomTestConstants.*
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

@Timeout(value = 30, unit = TimeUnit.MINUTES)
class FabricAPITest extends Specification implements GradleProjectTestTrait {
	private static final String API_VERSION = "0.0.0+loom"

	@Unroll
	def "build and run (gradle #version)"() {
		setup:
			def gradle = gradleProject(
					repo: "https://github.com/FabricMC/fabric.git",
					commit: "fc40aa9d88e9457957bdf3f8cec9698846828cd3",
					version: version,
					patch: "fabric_api"
			)

			// Set the version to something constant
			gradle.buildGradle.text = gradle.buildGradle.text.replace('Globals.baseVersion + "+" + (ENV.GITHUB_RUN_NUMBER ? "" : "local-") + getBranch()', "\"$API_VERSION\"")

			def server = ServerRunner.create(gradle.projectDir, "1.17.1")
										.withMod(gradle.getOutputFile("fabric-api-${API_VERSION}.jar"))
		when:
			def result = gradle.run(tasks: ["build", "publishToMavenLocal"], args: ["--parallel", "-x", "check"]) // Note: checkstyle does not appear to like being ran in a test runner
			gradle.printOutputFiles()

			def serverResult = server.run()
		then:
			result.task(":build").outcome == SUCCESS

			serverResult.successful()
			serverResult.output.contains("fabric@$API_VERSION")
		where:
			version << STANDARD_TEST_VERSIONS
	}
}
