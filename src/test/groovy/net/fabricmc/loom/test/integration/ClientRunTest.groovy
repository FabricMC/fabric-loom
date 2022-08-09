/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022 FabricMC
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
import net.fabricmc.loom.test.util.RobotTrait
import org.junit.jupiter.api.Disabled
import spock.lang.Specification
import spock.lang.Timeout
import spock.lang.Unroll

import java.util.concurrent.TimeUnit

import static net.fabricmc.loom.test.LoomTestConstants.PRE_RELEASE_GRADLE
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

@Timeout(value = 20, unit = TimeUnit.MINUTES)
@Disabled // Dont run on CI, it wont work.
class ClientRunTest extends Specification implements GradleProjectTestTrait, RobotTrait {
	@Unroll
	def "Run client (minecraft #version)"() {
		setup:
			def gradle = gradleProject(project: "minimalBase", version: PRE_RELEASE_GRADLE)
			gradle.buildGradle << """
                dependencies {
                    minecraft "com.mojang:minecraft:${version}"
                    mappings loom.layered() {
					}

                    modImplementation "net.fabricmc:fabric-loader:0.14.9"
                }
			"""

		when:
			waitForAndClick("quit")

			def result = gradle.run(task: "runClient")

		then:
			result.task(":runClient").outcome == SUCCESS

		where:
			version 		| _
			'1.19.2'		| _
			'1.18'			| _
	}
}
