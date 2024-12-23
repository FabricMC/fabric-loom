/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021-2023 FabricMC
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

import java.util.concurrent.TimeUnit

import spock.lang.Specification
import spock.lang.Timeout
import spock.lang.Unroll

import net.fabricmc.loom.test.util.GradleProjectTestTrait
import net.fabricmc.loom.test.util.ServerRunner
import net.fabricmc.loom.util.ZipUtils

import static net.fabricmc.loom.test.LoomTestConstants.*
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

@Timeout(value = 30, unit = TimeUnit.MINUTES)
class FabricAPITest extends Specification implements GradleProjectTestTrait {
	@Unroll
	def "build and run (gradle #version, mixin ap disabled: #disableMixinAp)"() {
		setup:
		def gradle = gradleProject(
				repo: "https://github.com/FabricMC/fabric.git",
				commit: "d70d2c06bb8fafdb72c6778b29fb050618015ab3",
				version: version,
				patch: "fabric_api"
				)

		// Disable the mixin ap if needed. Fabric API is a large enough test project to see if something breaks.
		if (disableMixinAp) {
			gradle.buildGradle << """
				allprojects {
					loom.mixin.useLegacyMixinAp = false
				}
				""".stripIndent()
		}

		def minecraftVersion = "1.21.4"
		def server = ServerRunner.create(gradle.projectDir, minecraftVersion)
				.withMod(gradle.getOutputFile("fabric-api-999.0.0.jar"))

		// Test that the dependent mod can be built against the previously built fabric-api
		def dependentMod = gradleProject(project: "minimalBase", version: version)
		dependentMod.buildGradle << """
				repositories {
					mavenLocal()
				}

				loom {
					loom.mixin.useLegacyMixinAp = ${!disableMixinAp}
				}

				dependencies {
                    minecraft "com.mojang:minecraft:${minecraftVersion}"
                    mappings "net.fabricmc:yarn:${minecraftVersion}+build.2:v2"

					modImplementation "net.fabricmc.fabric-api:fabric-api:999.0.0"
                }
		"""
		when:
		def result = gradle.run(tasks: [
			"clean",
			"build",
			"publishToMavenLocal"
		], args: [
			"--parallel",
			"-x",
			"check",
			"-x",
			"runDatagen",
			"-x",
			"runGametest"
		], configurationCache: false) // Note: checkstyle does not appear to like being ran in a test runner
		gradle.printOutputFiles()

		def serverResult = server.run()
		def dependentModResult = dependentMod.run(task: "build")

		then:
		result.task(":build").outcome == SUCCESS

		def biomeApiJar = new File(gradle.mavenLocalDir, "net/fabricmc/fabric-api/fabric-biome-api-v1/999.0.0/fabric-biome-api-v1-999.0.0.jar")
		new File(gradle.mavenLocalDir, "net/fabricmc/fabric-api/fabric-biome-api-v1/999.0.0/fabric-biome-api-v1-999.0.0-sources.jar").exists()
		def manifest = ZipUtils.unpack(biomeApiJar.toPath(), "META-INF/MANIFEST.MF").toString()

		if (disableMixinAp) {
			manifest.contains("Fabric-Loom-Mixin-Remap-Type=static")
		} else {
			manifest.contains("Fabric-Loom-Mixin-Remap-Type=mixin")
		}

		// Check that a client mixin exists
		def blockViewApiJar = new File(gradle.mavenLocalDir, "net/fabricmc/fabric-api/fabric-block-view-api-v2/999.0.0/fabric-block-view-api-v2-999.0.0.jar")
		ZipUtils.contains(blockViewApiJar.toPath(), "net/fabricmc/fabric/mixin/blockview/client/ChunkRendererRegionBuilderMixin.class")

		serverResult.successful()
		serverResult.output.contains("- fabric-api 999.0.0")

		dependentModResult.task(":build").outcome == SUCCESS

		where:
		[version, disableMixinAp] << [
			[PRE_RELEASE_GRADLE],
			[false, true].shuffled()
		].combinations()
	}
}
