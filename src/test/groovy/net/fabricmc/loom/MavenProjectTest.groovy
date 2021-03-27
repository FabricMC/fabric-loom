/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016, 2017, 2018 FabricMC
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

package net.fabricmc.loom

import net.fabricmc.loom.util.ArchiveAssertionsTrait
import net.fabricmc.loom.util.MockMavenServerTrait
import spock.lang.Specification
import spock.lang.Stepwise
import spock.lang.Unroll
import spock.util.environment.RestoreSystemProperties

import static java.lang.System.setProperty
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

/**
 * This tests publishing a range of versions and then tries to resolve and build against them
 */
@Stepwise
class MavenProjectTest extends Specification implements MockMavenServerTrait, ArchiveAssertionsTrait {
	@RestoreSystemProperties
	@Unroll
	def "publish lib #version"() {
		given:
			setProperty('loom.test.version', version)
			library = true
		when:
			def result = create("publish")
		then:
			result.task(":publish").outcome == SUCCESS
			hasArchiveEntry("fabric-example-lib-${version}.jar", "net/fabricmc/example/ExampleLib.class")
		where:
			version           | _
			'1.0.0'           | _
			'1.1.0'           | _
			'1.1.1'           | _
			'1.2.0+meta'      | _
			'2.0.0-SNAPSHOT'  | _
			'2.0.0-SNAPSHOT'  | _ // Publish this twice to give ourselves a bit of a challenge
			'master-SNAPSHOT' | _
	}

	@RestoreSystemProperties
	@Unroll
	def "resolve #version"() {
		given:
			setProperty('loom.test.resolve', "com.example:fabric-example-lib:${version}")
			library = false
		when:
			def result = create("build")
		then:
			result.task(":build").outcome == SUCCESS
			hasArchiveEntry("fabric-example-mod-1.0.0.jar", "net/fabricmc/examplemod/ExampleMod.class")
		where:
			version                      | _
			'1.0.0'                      | _
			'1.1.+'                      | _
			'1.2.0+meta'                 | _
			'2.0.0-SNAPSHOT'             | _
			'master-SNAPSHOT'            | _
			'1.0.0:classifier'           | _
			'1.1.+:classifier'           | _
			'1.2.0+meta:classifier'      | _
			'2.0.0-SNAPSHOT:classifier'  | _
			'master-SNAPSHOT:classifier' | _
	}

	// Set to true when to build and publish the mavenLibrary
	private boolean library = false

	@Override
	String name() {
		library ? "mavenLibrary" : "maven"
	}

	@Override
	void cleanupRun() {
		["src", "build", ".gradle/loom-cache", "build.gradle", "settings.gradle"].each {
			assert new File(testProjectDir, it).deleteDir()
		}
	}
}
