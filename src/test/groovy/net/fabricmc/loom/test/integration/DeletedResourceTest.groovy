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

import net.fabricmc.loom.test.util.GradleProjectTestTrait
import spock.lang.Specification
import spock.lang.Unroll

import static net.fabricmc.loom.test.LoomTestConstants.STANDARD_TEST_VERSIONS
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

// Tests that deleted files don't remain in built jars after a rebuild.
// See https://github.com/FabricMC/fabric-loom/issues/1270.
class DeletedResourceTest extends Specification implements GradleProjectTestTrait {
	@Unroll
	def "build (gradle #version)"() {
		// Initial conditions: a project with a resource file to delete.
		setup:
		def gradle = gradleProject(project: "simple", version: version)
		def deletedFile = new File(gradle.projectDir, "src/main/resources/foo.txt")
		deletedFile.text = "hello, world!"
		gradle.run(task: "build")

		when:
		// Delete the resource, then run another build.
		deletedFile.delete()
		def result = gradle.run(task: "build")

		then:
		result.task(":build").outcome == SUCCESS
		!gradle.hasOutputZipEntry("fabric-example-mod-1.0.0.jar", "foo.txt")
		!gradle.hasOutputZipEntry("fabric-example-mod-1.0.0-sources.jar", "foo.txt")
		!gradle.hasOutputZipEntry("fabric-example-mod-1.0.0-no-remap.jar", "foo.txt")
		!gradle.hasOutputZipEntry("fabric-example-mod-1.0.0-no-remap-sources.jar", "foo.txt")

		where:
		version << STANDARD_TEST_VERSIONS
	}
}
