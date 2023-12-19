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

import static net.fabricmc.loom.test.LoomTestConstants.STANDARD_TEST_VERSIONS
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class IncludedJarsTest extends Specification implements GradleProjectTestTrait {
	@Unroll
	def "included jars (gradle #version)"() {
		setup:
		def gradle = gradleProject(project: "includedJars", version: version)

		when:
		def result = gradle.run(tasks: ["remapJar"])

		then:
		result.task(":remapJar").outcome == SUCCESS

		// Assert directly declared dependencies are present
		gradle.hasOutputZipEntry("includedJars.jar", "META-INF/jars/log4j-core-2.22.0.jar")
		gradle.hasOutputZipEntry("includedJars.jar", "META-INF/jars/adventure-text-serializer-gson-4.14.0.jar")

		// But not transitives.
		!gradle.hasOutputZipEntry("includedJars.jar", "META-INF/jars/log4j-api-2.22.0.jar")
		!gradle.hasOutputZipEntry("includedJars.jar", "META-INF/jars/adventure-api-4.14.0.jar")

		where:
		version << STANDARD_TEST_VERSIONS
	}
}
