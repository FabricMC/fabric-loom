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

import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification

import net.fabricmc.loom.test.util.GradleProjectTestTrait

class ProjectDependencyMetadataTest extends Specification implements GradleProjectTestTrait {
	def "project metadata is classified by dependency scope"() {
		given:
		def gradle = gradleProject(project: "sourceMetadata")
		List<String> compileRuntime = scope == "implementation" || scope == "clientImplementation" ? ["producer"] : []
		List<String> clientOnly = scope == "clientImplementation" ? ["producer"] : []
		verifyContext(gradle, remapped, ["producer"], compileRuntime, clientOnly)
		List<String> arguments = ["-PdependencyScope=" + scope]

		if (remapped) {
			arguments.add("-Premapped")
		}

		if (scope == "clientImplementation") {
			arguments.add("-Psplit")
		}

		when:
		def result = gradle.run(task: ":consumer:help", isloatedProjects: true, args: arguments)

		then:
		result.task(":consumer:help").outcome == TaskOutcome.SUCCESS
		!new File(gradle.projectDir, "producer/build").exists()

		where:
		remapped | scope
		false    | "implementation"
		true     | "implementation"
		false    | "compileOnly"
		true     | "compileOnly"
		false    | "runtimeOnly"
		true     | "runtimeOnly"
		false    | "clientImplementation"
		true     | "clientImplementation"
	}

	def "existing project jars do not change direct metadata discovery"() {
		given:
		def gradle = gradleProject(project: "sourceMetadata")
		verifyContext(gradle, remapped, ["producer"], ["producer"], [])
		gradle.run(tasks: [
			":producer:jar",
			":indirect:jar"
		], isloatedProjects: true, args: remapped ? ["-Premapped"] : [])

		when:
		def result = gradle.run(task: ":consumer:help", isloatedProjects: true, args: remapped ? ["-Premapped"] : [])

		then:
		result.task(":consumer:help").outcome == TaskOutcome.SUCCESS

		where:
		remapped << [false, true]
	}

	def "explicit project metadata opt-out is retained"() {
		given:
		def gradle = gradleProject(project: "sourceMetadata")
		verifyContext(gradle, remapped, [], [], [])
		List<String> arguments = [
			"-Pfabric.loom.disableProjectDependentMods=true"
		]

		if (remapped) {
			arguments.add("-Premapped")
		}

		when:
		def result = gradle.run(task: ":consumer:help", isloatedProjects: true, args: arguments)

		then:
		result.task(":consumer:help").outcome == TaskOutcome.SUCCESS

		where:
		remapped << [false, true]
	}

	def "composite dependency metadata reaches the spec context"() {
		given:
		def gradle = gradleProject(project: "sourceMetadata")
		verifyContext(gradle, remapped, ["producer"], ["producer"], [])
		new File(gradle.projectDir, "settings.gradle").text = '''rootProject.name = 'source-metadata-test'
include 'consumer', 'plain'
includeBuild('producer') {
	dependencySubstitution {
		substitute module('example:producer') using project(':')
	}
}
'''
		new File(gradle.projectDir, "producer/settings.gradle").text = '''rootProject.name = 'producer'
include 'indirect'
project(':indirect').projectDir = file('../indirect')
'''
		def consumer = new File(gradle.projectDir, "consumer/build.gradle")
		consumer.text = consumer.text.replace("project(':producer')", "'example:producer:1.0.0'")

		when:
		def result = gradle.run(task: ":consumer:help", isloatedProjects: true, args: remapped ? ["-Premapped"] : [])

		then:
		result.task(":consumer:help").outcome == TaskOutcome.SUCCESS
		!new File(gradle.projectDir, "producer/build").exists()

		where:
		remapped << [false, true]
	}

	private static void verifyContext(GradleProject gradle, boolean remapped, List<String> all, List<String> compileRuntime, List<String> clientOnly) {
		String contextClass = remapped ? "RemappedSpecContext" : "DeobfSpecContext"
		new File(gradle.projectDir, "consumer/build.gradle") << """
afterEvaluate {
	def context = net.fabricmc.loom.configuration.processors.speccontext.${contextClass}.create(project)
	assert context.modDependencies()*.id == ${all.inspect()}
	assert context.modDependenciesCompileRuntime()*.id == ${compileRuntime.inspect()}
	assert context.modDependenciesCompileRuntimeClient()*.id == ${clientOnly.inspect()}
}
"""
	}
}
