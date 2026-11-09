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

package net.fabricmc.loom.test.integration.noRemap

import org.apache.commons.io.FileUtils
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification

import net.fabricmc.loom.test.util.GradleProjectTestTrait

class ModMetadataTest extends Specification implements GradleProjectTestTrait {
	def "source metadata resolves without producer outputs"() {
		given:
		def gradle = gradleProject(project: "sourceMetadata")
		verifyMetadata(gradle)

		if (overrideMetadata) {
			def overrides = new File(gradle.projectDir, "producer/overrides")
			FileUtils.copyDirectory(new File(gradle.projectDir, "producer/extra-resources"), overrides)
			FileUtils.copyFile(new File(gradle.projectDir, "producer/metadata/fabric.mod.json"), new File(overrides, "selected.json"))
		}

		when:
		def result = gradle.run(task: ":consumer:help", isloatedProjects: isolated, args: overrideMetadata ? ["-PoverrideMetadata"] : [])

		then:
		result.task(":consumer:help").outcome == TaskOutcome.SUCCESS
		!new File(gradle.projectDir, "producer/build").exists()

		where:
		isolated | overrideMetadata
		false    | false
		true     | false
		true     | true
	}

	def "metadata file changes affect configuration cache reuse"() {
		given:
		def gradle = gradleProject(project: "sourceMetadata")
		verifyMetadata(gradle)
		new File(gradle.projectDir, "producer/extra-resources/unrelated.txt").text = "unrelated"
		def options = [task: ":consumer:help", isloatedProjects: true]
		// Initial Minecraft preparation can invalidate the first configuration.
		gradle.run(options)
		gradle.run(options)

		when:
		def cached = gradle.run(options)

		then:
		cached.output.contains("Reusing configuration cache.")

		when:
		new File(gradle.projectDir, path) << "\n"
		def edited = gradle.run(options)

		then:
		edited.output.contains("Reusing configuration cache.") == reuseCache

		where:
		path                                                                 | reuseCache
		"producer/metadata/fabric.mod.json"                                   | false
		"producer/extra-resources/tweakers/producer.accesswidener"             | false
		"producer/extra-resources/docs/docs.tiny"                              | false
		"producer/extra-resources/unrelated.txt"                              | true
	}

	private static void verifyMetadata(GradleProject gradle) {
		new File(gradle.projectDir, "consumer/build.gradle") << '''
afterEvaluate {
	def resolver = new net.fabricmc.loom.configuration.processors.speccontext.ModMetadataResolver(project)
	assert resolver.resolve(configurations.compileClasspath)*.id == ['producer']
}
'''
	}
}
