/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2021 FabricMC
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

import com.google.common.hash.HashCode
import com.google.common.hash.Hashing
import com.google.common.io.Files
import spock.lang.Specification
import spock.lang.Unroll
import spock.util.environment.RestoreSystemProperties

import net.fabricmc.loom.test.util.GradleProjectTestTrait

import static java.lang.System.setProperty
import static net.fabricmc.loom.test.LoomTestConstants.*
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class ReproducibleBuildTest extends Specification implements GradleProjectTestTrait {
	@RestoreSystemProperties
	@Unroll
	def "build (gradle #version)"() {
		setup:
		def gradle = gradleProject(project: "reproducible", version: version)

		when:
		setProperty('loom.test.reproducible', 'true')
		def result = gradle.run(task: "build")

		then:
		result.task(":build").outcome == SUCCESS
		generateMD5(gradle.getOutputFile("fabric-example-mod-1.0.0.jar")) == modHash
		generateMD5(gradle.getOutputFile("fabric-example-mod-1.0.0-sources.jar")) == sourceHash

		where:
		version              | modHash                               | sourceHash
		DEFAULT_GRADLE       | "207bd75aa34fc996a97e962dd98b61d5"    | "8e8fac2a5e32fc872e6cf0f9ccc55cfd"
		PRE_RELEASE_GRADLE   | "207bd75aa34fc996a97e962dd98b61d5"    | "8e8fac2a5e32fc872e6cf0f9ccc55cfd"
	}

	String generateMD5(File file) {
		HashCode hash = Files.asByteSource(file).hash(Hashing.md5())
		return hash.asBytes().encodeHex() as String
	}
}
