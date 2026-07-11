/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2026 FabricMC
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

import org.gradle.testkit.runner.BuildResult
import org.intellij.lang.annotations.Language
import spock.lang.Specification
import spock.lang.Unroll

import net.fabricmc.loom.test.LoomTestVersions
import net.fabricmc.loom.test.util.GradleProjectTestTrait
import net.fabricmc.loom.util.ZipUtils

import static net.fabricmc.loom.test.LoomTestConstants.STANDARD_TEST_VERSIONS
import static org.gradle.testkit.runner.TaskOutcome.FAILED
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class InterfaceInjectionTest extends Specification implements GradleProjectTestTrait {
	@Language("JAVA")
	private static final String INJECTED_INTERFACE_WITH_ABSTRACT_METHOD = "interface TestItf { void foo(); }"

	@Unroll
	def "interface injection (gradle #version)"() {
		setup:
		def gradle = gradleProject(project: "interfaceInjection", version: version)
		ZipUtils.pack(new File(gradle.projectDir, "dummyDependency").toPath(), new File(gradle.projectDir, "dummy.jar").toPath())

		when:
		def result = gradle.run(task: "build")

		then:
		result.task(":build").outcome == SUCCESS

		where:
		version << STANDARD_TEST_VERSIONS
	}

	@Unroll
	def "interface injection without intermediary (gradle #version)"() {
		setup:
		def gradle = gradleProject(project: "interfaceInjectionNoIntermediary", version: version)
		ZipUtils.pack(new File(gradle.projectDir, "dummyDependency").toPath(), new File(gradle.projectDir, "dummy.jar").toPath())

		when:
		def result = gradle.run(task: "build")

		then:
		result.task(":build").outcome == SUCCESS

		where:
		version << STANDARD_TEST_VERSIONS
	}

	@Unroll
	def "interface injection without intermediary genSources (gradle #version)"() {
		setup:
		def gradle = gradleProject(project: "interfaceInjectionNoIntermediary", version: version)
		ZipUtils.pack(new File(gradle.projectDir, "dummyDependency").toPath(), new File(gradle.projectDir, "dummy.jar").toPath())

		when:
		def result = gradle.run(task: "genSources")

		then:
		result.task(":genSources").outcome == SUCCESS

		where:
		version << STANDARD_TEST_VERSIONS
	}

	@Unroll
	def "Resolve custom FMJ"() {
		setup:
		GradleProject gradle = gradleProject(project: "fmjPathConfig", version: version)

		when:
		BuildResult result = gradle.run(task: "build", args: ["-PoverrideFMJ=true"])

		then:
		result.task(":build").outcome == SUCCESS

		where:
		version << STANDARD_TEST_VERSIONS
	}

	@Unroll
	def "Fail to find FMJ"() {
		setup:
		GradleProject gradle = gradleProject(project: "fmjPathConfig", version: version)

		when:
		BuildResult result = gradle.run(task: "build", expectFailure: true)

		then:
		result.task(":build") == null

		where:
		version << STANDARD_TEST_VERSIONS
	}

	@Unroll
	def "validate invalid injected interfaces (gradle #version, class tweaker: #useCt)"() {
		setup:
		def gradle = gradleProject(project: 'minimalBaseNoRemap', version: version)
		gradle.buildGradle << """
 			dependencies {
 				minecraft "com.mojang:minecraft:26.2"
 				implementation "${LoomTestVersions.FABRIC_LOADER.mavenNotation()}"
 			}

 			tasks.register('validateInjectedInterfaces', net.fabricmc.loom.task.ValidateInjectedInterfacesTask) {
 				problemReportingOptions.displayGithubActionsAnnotations = true
 			}

 			tasks.named('check') {
 				dependsOn 'validateInjectedInterfaces'
 			}
			"""
		new File(gradle.projectDir, 'src/main/java').mkdirs()
		new File(gradle.projectDir, 'src/main/resources').mkdirs()
		new File(gradle.projectDir, 'src/main/java/TestItf.java').text = INJECTED_INTERFACE_WITH_ABSTRACT_METHOD

		if (useCt) {
			new File(gradle.projectDir, 'src/main/resources/fabric.mod.json').text = """
				{
					"schemaVersion": 1,
					"id": "test",
					"version": "1.0.0",
					"accessWidener": "test.classtweaker"
				}
				"""
			new File(gradle.projectDir, 'src/main/resources/test.classtweaker').text = """\
				classTweaker v2 official
				inject-interface net/minecraft/world/level/block/Block TestItf
				""".stripIndent()
		} else {
			new File(gradle.projectDir, 'src/main/resources/fabric.mod.json').text = """
				{
					"schemaVersion": 1,
					"id": "test",
					"version": "1.0.0",
					"custom": {
						"loom:injected_interfaces": {
							"net/minecraft/class_2248": ["TestItf"]
						}
					}
				}
				"""
		}

		when:
		def result = gradle.run(task: 'build', expectFailure: true)

		then:
		result.task(':validateInjectedInterfaces').outcome == FAILED
		result.output.contains('Injected interface TestItf has abstract method foo()V')
		// Check that the output has the GH Actions command.
		result.output.lines()
				.anyMatch {
					// Note: the final .+ is for catching the rest of the message. We don't care about the exact
					// message contents in this test.
					it.matches("::error file=.+TestItf\\.java,line=1,title=Abstract method in injected interface::Method TestItf\\.foo\\(\\)V is abstract\\..+")
				}

		where:
		version << STANDARD_TEST_VERSIONS
		useCt << [true, false]
	}
}
