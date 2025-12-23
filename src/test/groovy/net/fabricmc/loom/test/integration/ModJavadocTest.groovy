/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022-2025 FabricMC
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

import spock.lang.Specification
import spock.lang.Unroll

import net.fabricmc.loom.test.util.GradleProjectTestTrait
import net.fabricmc.loom.util.ZipUtils

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class ModJavadocTest extends Specification implements GradleProjectTestTrait {
	@Unroll
	def "mod javadoc (#projectName)"() {
		setup:
		def gradle = gradleProject(project: projectName)
		ZipUtils.pack(new File(gradle.projectDir, "dummyDependency").toPath(), new File(gradle.projectDir, "dummy.jar").toPath())

		when:
		def result = gradle.run(task: "genSources")
		def blocks = getClassSource(gradle, targetSourceFile, targetSourceJar)

		then:
		result.task(":genSources").outcome == SUCCESS
		blocks.contains("An example of a mod added class javadoc")
		blocks.contains("An example of a mod added field javadoc")
		blocks.contains("An example of a mod added method javadoc")

		where:
		projectName | targetSourceFile | targetSourceJar
		'modJavadoc' | 'net/minecraft/block/Blocks.java' | '1.17.1-net.fabricmc.yarn.1_17_1.1.17.1+build.59-v2'
		'modJavadocNoRemap' | 'net/minecraft/world/level/block/Blocks.java' | '26.1-snapshot-1'
	}

	private static String getClassSource(GradleProject gradle, String classname, String gameVersion) {
		File sourcesJar = gradle.getGeneratedLocalSources(gameVersion)
		return new String(ZipUtils.unpack(sourcesJar.toPath(), classname), StandardCharsets.UTF_8)
	}
}
