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

import java.nio.charset.StandardCharsets

import groovy.transform.Immutable
import spock.lang.Specification

import net.fabricmc.loom.test.util.GradleProjectTestTrait
import net.fabricmc.loom.util.ZipUtils

import static net.fabricmc.loom.test.LoomTestConstants.STANDARD_TEST_VERSIONS
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class UnpickTest extends Specification implements GradleProjectTestTrait {
	static final MappingInfo V1 = new MappingInfo(
		minecraft: "21w13a",
		yarn: "21w13a+build.30:v2",
		name: "21w13a-net.fabricmc.yarn.21w13a.21w13a+build.30-v2",
		searchString: "Block.DEFAULT_SET_BLOCK_STATE_FLAG",
	)
	static final MappingInfo V2_NAMED = new MappingInfo(
		minecraft: "25w32a",
		yarn: "25w32a+build.8:v2",
		name: "25w32a-net.fabricmc.yarn.25w32a.25w32a+build.8-v2",
		searchString: "Block.NOTIFY_ALL",
	)

	// Test to make sure that constants are unpicked in the decompiled source across multiple versions.
	def "unpick decompile #version #useCache #info"() {
		setup:
		def gradle = gradleProject(project: "minimalBase", version: version)
		gradle.buildGradle << """
			dependencies {
				minecraft "com.mojang:minecraft:${info.minecraft}"
				mappings "net.fabricmc:yarn:${info.yarn}"
				modImplementation "net.fabricmc:fabric-loader:0.17.2"
			}
		"""

		when:
		def result = gradle.run(tasks: useCache ? [
			"genSourcesWithVineflower",
			"--info"
		] : [
			"genSourcesWithVineflower",
			"--no-use-cache",
			"--info"
		])
		then:
		result.task(":genSourcesWithVineflower").outcome == SUCCESS
		getClassSource(gradle, "net/minecraft/block/CakeBlock.java", info.name).contains(info.searchString)
		result.output.contains(useCache ? "Using decompile cache." : "Not using decompile cache.")

		where:
		[version, useCache, info] << [
			STANDARD_TEST_VERSIONS,
			[true, false],
			[V1, V2_NAMED]
		].combinations()
	}

	// Test to make sure that we can compile against constants from yarn using the v1 unpick metadata format.
	def "unpick v1 constants"() {
		setup:
		def gradle = gradleProject(project: "unpick", version: version)

		when:
		def result = gradle.run(task: "build")

		then:
		result.task(":build").outcome == SUCCESS

		where:
		version << STANDARD_TEST_VERSIONS
	}

	private static String getClassSource(GradleProject gradle, String classname, String mappings) {
		File sourcesJar = gradle.getGeneratedSources(mappings)
		return new String(ZipUtils.unpack(sourcesJar.toPath(), classname), StandardCharsets.UTF_8)
	}

	@Immutable
	private static class MappingInfo {
		final String minecraft
		final String yarn
		final String name
		final String searchString
	}
}
