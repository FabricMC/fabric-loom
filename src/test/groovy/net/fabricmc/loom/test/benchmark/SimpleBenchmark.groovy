/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022 FabricMC
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

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

/**
 * Run this class, passing a working dir as the first argument.
 * Allow for one warm up run before profiling, follow up runs should not be using the network.
 */
@Singleton
class SimpleBenchmark implements GradleProjectTestTrait {
	def run(File dir) {
		def gradle = gradleProject(
				project: "minimalBase",
				version: LoomTestConstants.PRE_RELEASE_GRADLE,
				projectDir: new File(dir, "project"),
				gradleHomeDir: new File(dir, "gradlehome")
				)

		gradle.buildGradle << '''
                dependencies {
                    minecraft "com.mojang:minecraft:1.18.1"
                    mappings "net.fabricmc:yarn:1.18.1+build.17:v2"
                    modImplementation "net.fabricmc.fabric-api:fabric-api:0.45.1+1.18"
                }
            '''

		def timeStart = new Date()

		def result = gradle.run(tasks: ["clean", "build"])

		def timeStop = new Date()
		TimeDuration duration = TimeCategory.minus(timeStop, timeStart)
		println(duration)

		assert result.task(":build").outcome == SUCCESS

		System.exit(0)
	}

	static void main(String[] args) {
		getInstance().run(new File(args[0]))
	}
}
