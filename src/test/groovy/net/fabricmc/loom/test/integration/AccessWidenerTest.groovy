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

import net.fabricmc.loom.test.util.GradleProjectTestTrait
import org.zeroturnaround.zip.ZipUtil
import spock.lang.Specification
import spock.lang.Unroll

import static net.fabricmc.loom.test.LoomTestConstants.*
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class AccessWidenerTest extends Specification implements GradleProjectTestTrait {
	@Unroll
	def "accesswidener (gradle #version)"() {
		setup:
			def gradle = gradleProject(project: "accesswidener", version: version)
		when:
			def result = gradle.run(task: "build")
		then:
			result.task(":build").outcome == SUCCESS
			gradle.getOutputZipEntry("fabric-example-mod-1.0.0.jar", "modid.accesswidener") == expected().replaceAll('\r', '')
		where:
			version << STANDARD_TEST_VERSIONS
	}

	String expected() {
		new File("src/test/resources/accesswidener/expected.accesswidener").text
	}

	@Unroll
	def "transitive accesswidener (gradle #version)"() {
		setup:
			def gradle = gradleProject(project: "transitiveAccesswidener", version: version)
			ZipUtil.pack(new File(gradle.projectDir, "dummyDependency"), new File(gradle.projectDir, "dummy.jar"))

		when:
			def result = gradle.run(task: "build")

		then:
			result.task(":build").outcome == SUCCESS

		where:
			version << STANDARD_TEST_VERSIONS
	}
}
