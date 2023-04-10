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

import static net.fabricmc.loom.test.LoomTestConstants.*
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class DependencyOrderTest extends Specification implements GradleProjectTestTrait {
	// Regression test for a bug introduced in 1.1 development where
	// if Fabric Loader is resolved after another mod dependency,
	// Gradle will crash because loaderLibraries has been resolved before
	// Loader's dependencies have been added to it.
	@Unroll
	def "build with loader as the second dependency (gradle #version)"() {
		setup:
		def gradle = gradleProject(project: "minimalBase", version: version)
		gradle.buildGradle << """
            dependencies {
                minecraft 'com.mojang:minecraft:1.19.3'
                mappings 'net.fabricmc:yarn:1.19.3+build.5:v2'
                modApi 'net.fabricmc.fabric-api:fabric-api:0.73.0+1.19.3'
                modImplementation 'net.fabricmc:fabric-loader:0.14.13'
            }
            """.stripIndent()
		when:
		def result = gradle.run(task: "build")
		then:
		result.task(":build").outcome == SUCCESS
		where:
		version << STANDARD_TEST_VERSIONS
	}
}
