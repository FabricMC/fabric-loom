/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2023 FabricMC
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

class MultiMcVersionTest extends Specification implements GradleProjectTestTrait {
	static List<String> versions = [
		'fabric-1.14.4',
		'fabric-1.15',
		'fabric-1.15.2',
		'fabric-1.16',
		'fabric-1.16.5',
		'fabric-1.17',
		'fabric-1.17.1',
		'fabric-1.18',
		'fabric-1.18.2',
		'fabric-1.19',
		'fabric-1.19.3'
	]

	@Unroll
	def "build (gradle #version)"() {
		setup:
		def gradle = gradleProject(project: "multi-mc-versions", version: version)

		versions.forEach {
			// Make dir as its now required by Gradle
			new File(gradle.projectDir, it).mkdir()
		}

		when:
		def result = gradle.run(tasks: "build")

		then:
		result.task(":build").outcome == SUCCESS
		versions.forEach {
			result.task(":$it:build").outcome == SUCCESS
		}

		where:
		version << STANDARD_TEST_VERSIONS
	}
}
