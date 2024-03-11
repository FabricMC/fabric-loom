/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2018-2021 FabricMC
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
import net.fabricmc.loom.test.util.ServerRunner

import static net.fabricmc.loom.test.LoomTestConstants.*
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class KotlinTest extends Specification implements GradleProjectTestTrait {
	@Unroll
	def "kotlin build (gradle #version)"() {
		setup:
		def gradle = gradleProject(project: "kotlin", version: version)
		def server = ServerRunner.create(gradle.projectDir, "1.16.5")
				.withMod(gradle.getOutputFile("fabric-example-mod-0.0.1.jar"))
				.downloadMod(ServerRunner.FABRIC_LANG_KOTLIN, "fabric-language-kotlin-1.10.17+kotlin.1.9.22.jar")

		when:
		def result = gradle.run(tasks: [
			"build",
			"publishToMavenLocal"
		])
		def serverResult = server.run()

		then:
		result.task(":build").outcome == SUCCESS
		serverResult.successful()

		// Check POM file to see that it doesn't contain transitive deps of FLK.
		// See https://github.com/FabricMC/fabric-loom/issues/572.
		result.task(":publishToMavenLocal").outcome == SUCCESS
		def pom = new File(gradle.projectDir, "build/publications/mavenKotlin/pom-default.xml").text
		// FLK depends on kotlin-reflect unlike our test project.
		pom.contains('kotlin-reflect') == false

		where:
		version << STANDARD_TEST_VERSIONS
	}
}
