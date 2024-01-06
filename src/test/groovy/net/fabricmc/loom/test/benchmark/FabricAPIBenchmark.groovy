/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022-2024 FabricMC
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

package net.fabricmc.loom.test.benchmark

import groovy.time.TimeCategory
import groovy.time.TimeDuration

import net.fabricmc.loom.test.LoomTestConstants
import net.fabricmc.loom.test.util.GradleProjectTestTrait

/**
 * Run this class, passing a working dir as the first argument.
 * Allow for one warm up run before profiling, follow up runs should not be using the network.
 */
@Singleton
class FabricAPIBenchmark implements GradleProjectTestTrait {
	def run(File dir) {
		def gradle = gradleProject(
				version: LoomTestConstants.DEFAULT_GRADLE,
				projectDir: new File(dir, "project"),
				gradleHomeDir: new File(dir, "gradlehome"),
				allowExistingRepo: true,

				repo: "https://github.com/FabricMC/fabric.git",
				commit: "efa5891941a32589207dc58c2e77183d599465b8",
				patch: "fabric_api"
				)

		gradle.enableMultiProjectOptimisation()

		if (!gradle.buildGradle.text.contains("loom.mixin.useLegacyMixinAp")) {
			gradle.buildGradle << """
				allprojects {
					loom.mixin.useLegacyMixinAp = false
				}
				""".stripIndent()
		}

		def timeStart = new Date()

		def result = gradle.run(tasks: [
			"clean",
			"build",
			"-x",
			"test",
			"-x",
			"check",
			"-x",
			":fabric-data-generation-api-v1:runDatagen"
		], args: [])

		def timeStop = new Date()
		TimeDuration duration = TimeCategory.minus(timeStop, timeStart)
		println(duration)
	}

	static void main(String[] args) {
		getInstance().run(new File(args[0]))
		System.exit(0)
	}
}
