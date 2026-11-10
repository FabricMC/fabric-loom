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

import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.Unroll

import net.fabricmc.loom.test.util.GradleProjectTestTrait

import static net.fabricmc.loom.test.LoomTestConstants.STANDARD_TEST_VERSIONS
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class MultiProjectTest extends Specification implements GradleProjectTestTrait {
	@Unroll
	def "build (gradle #version)"() {
		setup:
		def gradle = gradleProject(project: "multiproject", version: version)

		when:
		def result = gradle.run(tasks: [
			"build",
			"genEclipseRuns",
			"vscode",
		])

		then:
		result.task(":build").outcome == SUCCESS
		result.task(":core:build").outcome == SUCCESS
		result.task(":example:build").outcome == SUCCESS

		gradle.hasOutputZipEntry("multiproject-1.0.0.jar", "META-INF/jars/example-1.0.0.jar")
		gradle.hasOutputZipEntry("multiproject-1.0.0.jar", "META-INF/jars/core-1.0.0.jar")
		gradle.hasOutputZipEntry("multiproject-1.0.0.jar", "META-INF/jars/fabric-api-base-0.2.1+9354966b7d.jar")

		where:
		version << STANDARD_TEST_VERSIONS
	}

	def "build with different sibling plugin classpaths"() {
		given:
		def gradle = gradleProject(project: "minimalBase")
		gradle.buildGradle.text = ''
		new File(gradle.projectDir, 'settings.gradle').text = "include 'first', 'second'"
		def loomClasspath = GradleRunner.create().withPluginClasspath().pluginClasspath.collect { File file ->
			"'${file.absolutePath.replace("\\", "/")}'"
		}.join(', ')

		for (String name : ['first', 'second']) {
			def childDirectory = new File(gradle.projectDir, name)
			def extraClasspath = new File(childDirectory, 'extra-classpath')
			extraClasspath.mkdirs()
			// Different plugin classpaths make Gradle load Loom separately for each sibling.
			new File(extraClasspath, 'marker.txt').text = name
			new File(childDirectory, 'build.gradle').text = """
                buildscript {
                    dependencies {
                        classpath files(${loomClasspath})
                        classpath files('extra-classpath')
                    }
                }

                apply plugin: 'net.fabricmc.fabric-loom-remap'

                version = '1.0.0'
                dependencies {
                    minecraft 'com.mojang:minecraft:1.16.5'
                    mappings 'net.fabricmc:yarn:1.16.5+build.5:v2'
                }
            """
			def source = new File(childDirectory, 'src/main/java/Example.java')
			source.parentFile.mkdirs()
			source.text = 'public class Example {}'
		}

		when:
		// Keep Loom off the common parent classpath so each sibling loads its own copy.
		def result = GradleRunner.create()
				.withProjectDir(gradle.projectDir)
				.withArguments('build', '--max-workers=1', '--stacktrace', '--gradle-user-home', gradle.gradleHomeDir.absolutePath)
				.build()

		then:
		result.task(':first:remapJar').outcome == SUCCESS
		result.task(':second:remapJar').outcome == SUCCESS
		result.output.count('Loom is loaded by multiple classloaders;') == 1
	}

	@Unroll
	def "classpath groups (gradle #version)"() {
		setup:
		def gradle = gradleProject(project: "multiproject", version: version)

		when:
		def result = gradle.run(tasks: [":generateDLIConfig",])

		then:
		result.task(":generateDLIConfig").outcome == SUCCESS

		where:
		version << STANDARD_TEST_VERSIONS
	}
}
