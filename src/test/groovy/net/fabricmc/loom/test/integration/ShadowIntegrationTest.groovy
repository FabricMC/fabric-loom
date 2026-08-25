/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2026 FabricMC
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

import java.util.zip.ZipFile

import spock.lang.Specification

import net.fabricmc.loom.test.util.GradleProjectTestTrait

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class ShadowIntegrationTest extends Specification implements GradleProjectTestTrait {
	def "shadow uses the standard Java runtime scope and feeds remapJar"() {
		setup:
		def gradle = gradleProject(project: "shadowIntegration")

		when:
		def first = gradle.run(tasks: ["remapJar"])
		def second = gradle.run(tasks: ["remapJar"])
		def third = second.output.contains("Reusing configuration cache") ? second : gradle.run(tasks: ["remapJar"])
		def shadowJar = new File(gradle.projectDir, "build/libs/shadowIntegration-1.0.0-all.jar")
		def remappedJar = new File(gradle.projectDir, "build/libs/shadowIntegration-1.0.0.jar")

		then:
		first.task(":shadowJar").outcome == SUCCESS
		first.task(":remapJar").outcome == SUCCESS
		third.output.contains("Reusing configuration cache")
		shadowJar.isFile()
		remappedJar.isFile()

		and:
		[shadowJar, remappedJar].each { jar ->
			assert hasEntry(jar, "org/apache/commons/lang3/StringUtils.class") // api
			assert hasEntry(jar, "com/google/gson/Gson.class") // implementation
			assert hasEntry(jar, "org/slf4j/Logger.class") // runtimeOnly
			assert !hasEntry(jar, "org/apache/commons/io/IOUtils.class") // compileOnly
			assert !hasEntry(jar, "javax/annotation/Nullable.class") // compileOnlyApi
			assert !hasEntry(jar, "org/junit/Assert.class") // testImplementation
			assert !hasEntry(jar, "net/minecraft/util/Identifier.class")
			assert !hasEntry(jar, "net/fabricmc/loader/api/FabricLoader.class")
			assert !hasEntry(jar, "org/apache/logging/log4j/core/Logger.class") // localRuntime
		}
	}

	def "shadow integration is independent of plugin application order"() {
		setup:
		def gradle = gradleProject(project: "shadowIntegration")
		gradle.buildGradle.text = gradle.buildGradle.text.replace(
				"id 'fabric-loom'\n\tid 'com.gradleup.shadow' version '9.6.1'",
				"id 'com.gradleup.shadow' version '9.6.1'\n\tid 'fabric-loom'")

		when:
		def result = gradle.run(tasks: ["remapJar"])

		then:
		result.task(":shadowJar").outcome == SUCCESS
		hasEntry(new File(gradle.projectDir, "build/libs/shadowIntegration-1.0.0.jar"), "com/google/gson/Gson.class")
	}

	def "explicit Shadow and remap inputs win over Loom conventions"() {
		setup:
		def gradle = gradleProject(project: "shadowIntegration")
		gradle.buildGradle << '''

configurations { deliberatelyEmpty }
tasks.shadowJar { configurations = [project.configurations.deliberatelyEmpty] }
tasks.remapJar { inputFile = tasks.jar.archiveFile }
'''

		when:
		def result = gradle.run(tasks: ["remapJar"])
		def remappedJar = new File(gradle.projectDir, "build/libs/shadowIntegration-1.0.0.jar")

		then:
		result.task(":remapJar").outcome == SUCCESS
		result.task(":shadowJar") == null
		!hasEntry(remappedJar, "com/google/gson/Gson.class")
	}

	private static boolean hasEntry(File file, String path) {
		new ZipFile(file).withCloseable { it.getEntry(path) != null }
	}
}
