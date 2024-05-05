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
		def result = gradle.run(task: task, configurationCache: true, isloatedProjects: false)
		def result2 = gradle.run(task: task, configurationCache: true, isloatedProjects: false)

		then:
		result.task(":${task}").outcome != FAILED
		result2.task(":${task}").outcome != FAILED

		where:
		task                      | _
		"help"                    | _
		"configureClientLaunch"   | _
		"jar"                     | _
		"check"                   | _
	}
}
