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
import net.fabricmc.loom.test.util.MockMavenServerTrait
import spock.lang.Specification
import spock.lang.Stepwise
import spock.lang.Unroll
import spock.util.environment.RestoreSystemProperties

import static net.fabricmc.loom.test.LoomTestConstants.*
import static java.lang.System.setProperty
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

/**
 * This tests publishing a range of versions and then tries to resolve and build against them
 */
@Stepwise
class MavenProjectTest extends Specification implements MockMavenServerTrait, GradleProjectTestTrait {
	@RestoreSystemProperties
	@Unroll
	def "publish lib #version #gradleVersion"() {
		setup:
			setProperty('loom.test.version', version)
			def gradle = gradleProject(project: "mavenLibrary", version: gradleVersion, sharedFiles: true)

		when:
			def result = gradle.run(tasks: ["clean", "publish"])

		then:
			result.task(":publish").outcome == SUCCESS
			gradle.hasOutputZipEntry("fabric-example-lib-${version}.jar", "net/fabricmc/example/ExampleLib.class")

		where:
			version           | gradleVersion
			'1.0.0'           | DEFAULT_GRADLE
			'1.0.0'           | PRE_RELEASE_GRADLE
			'1.1.0'           | DEFAULT_GRADLE
			'1.1.1'           | DEFAULT_GRADLE
			'1.2.0+meta'      | DEFAULT_GRADLE
			'2.0.0-SNAPSHOT'  | DEFAULT_GRADLE
			'2.0.0-SNAPSHOT'  | DEFAULT_GRADLE // Publish this twice to give ourselves a bit of a challenge
			'master-SNAPSHOT' | DEFAULT_GRADLE
	}

	@RestoreSystemProperties
	@Unroll
	def "resolve #version #gradleVersion"() {
		given:
			setProperty('loom.test.resolve', "com.example:fabric-example-lib:${version}")
			def gradle = gradleProject(project: "maven", version: gradleVersion, sharedFiles: true)

		when:
			def result = gradle.run(tasks: ["clean", "build"])

		then:
			result.task(":build").outcome == SUCCESS
			gradle.hasOutputZipEntry("fabric-example-mod-1.0.0.jar", "net/fabricmc/examplemod/ExampleMod.class")

		where:
			version                      | gradleVersion
			'1.0.0'                      | DEFAULT_GRADLE
			'1.0.0'                      | PRE_RELEASE_GRADLE
			'1.1.+'                      | DEFAULT_GRADLE
			'1.2.0+meta'                 | DEFAULT_GRADLE
			'2.0.0-SNAPSHOT'             | DEFAULT_GRADLE
			'master-SNAPSHOT'            | DEFAULT_GRADLE
			'1.0.0:classifier'           | DEFAULT_GRADLE
			'1.1.+:classifier'           | DEFAULT_GRADLE
			'1.2.0+meta:classifier'      | DEFAULT_GRADLE
			'2.0.0-SNAPSHOT:classifier'  | DEFAULT_GRADLE
			'master-SNAPSHOT:classifier' | DEFAULT_GRADLE
	}
}
