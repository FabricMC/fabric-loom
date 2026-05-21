/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2018-2025 FabricMC
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

import java.nio.file.Files
import java.nio.file.Path

import spock.lang.Specification
import spock.lang.Unroll

import net.fabricmc.loom.test.LoomTestVersions
import net.fabricmc.loom.test.util.GradleProjectTestTrait

import static net.fabricmc.loom.test.LoomTestConstants.PRE_RELEASE_GRADLE
import static net.fabricmc.loom.test.LoomTestConstants.STANDARD_TEST_VERSIONS
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class LegacyProjectTest extends Specification implements GradleProjectTestTrait {
	@Unroll
	def "legacy build (gradle #version)"() {
		setup:
		def gradle = gradleProject(project: "legacy", version: version)

		when:
		def result = gradle.run(task: "build")

		then:
		result.task(":build").outcome == SUCCESS

		where:
		version << STANDARD_TEST_VERSIONS
	}

	@Unroll
	def "Unsupported minecraft (minecraft #version)"() {
		setup:
		def gradle = gradleProject(project: "minimalBaseNoRemap", version: PRE_RELEASE_GRADLE)
		gradle.buildGradle << """
                dependencies {
                    minecraft "com.mojang:minecraft:${version}"

                    implementation "${LoomTestVersions.FABRIC_LOADER.mavenNotation()}"
                }
			"""

		when:
		def result = gradle.run(task: "configureClientLaunch")

		then:
		result.task(":configureClientLaunch").outcome == SUCCESS

		where:
		version 		| _
		'1.13.2'		| _
		'1.12.2'		| _
		'1.8.9'			| _
		'1.7.10'		| _
		'1.7'			| _
		'1.6.4'			| _
		'1.4.7'			| _
		'1.3.2'			| _
	}

	@Unroll
	def "Ancient minecraft (minecraft #version)"() {
		setup:
		def gradle = gradleProject(project: "minimalBaseNoRemap", version: PRE_RELEASE_GRADLE)
		gradle.buildGradle << """
				loom {
					clientOnlyMinecraftJar()
                }

                dependencies {
                    minecraft "com.mojang:minecraft:${version}"

                    implementation "${LoomTestVersions.FABRIC_LOADER.mavenNotation()}"
                }
			"""

		when:
		def result = gradle.run(task: "configureClientLaunch")

		then:
		result.task(":configureClientLaunch").outcome == SUCCESS

		where:
		version 		| _
		'1.2.5'			| _
		'b1.8.1'		| _
		'a1.2.5'		| _
	}

	@Unroll
	def "Legacy merged"() {
		setup:
		def mappings = Path.of("src/test/resources/mappings/1.2.5-intermediary.tiny.zip").toAbsolutePath()
		def gradle = gradleProject(project: "minimalBase", version: PRE_RELEASE_GRADLE, gradleHomeDir: File.createTempDir())

		gradle.buildGradle << """
                dependencies {
                    minecraft "com.mojang:minecraft:1.2.5"
                    mappings loom.layered() {
						// No names
					}

                    modImplementation "${LoomTestVersions.FABRIC_LOADER.mavenNotation()}"
                }
			"""
		gradle.buildSrc("legacyMergedIntermediary")

		when:
		def result = gradle.run(task: "build", args: [
			"-Ploom.test.legacyMergedIntermediary.mappingPath=${mappings}"
		])

		then:
		result.task(":build").outcome == SUCCESS
	}

	def "Legacy single jar version with mappings but no intermediates"() {
		setup:
		def mappings = Path.of('src/test/resources/mappings/0.30-minimal.tiny')
		def gradle = gradleProject(project: "minimalBase", version: PRE_RELEASE_GRADLE)

		Files.copy(mappings, gradle.projectDir.toPath().resolve('mappings.tiny'))
		gradle.buildGradle << """
				loom.noIntermediateMappings()
				loom.productionNamespace = "official"

				dependencies {
					minecraft "com.mojang:minecraft:c0.30_01c"
					mappings loom.layered {
						it.mappings file("mappings.tiny")
					}

					modImplementation "${LoomTestVersions.FABRIC_LOADER.mavenNotation()}"
				}
			"""
		def sourceFile = new File(gradle.projectDir, 'src/main/java/Test.java')
		sourceFile.parentFile.mkdirs()
		sourceFile.text = """
			public final class Test {
				public static void foo() {
					// Reference a mapped class
					System.out.println(com.mojang.minecraft.Minecraft.class);
				}
			}
			"""

		when:
		def result = gradle.run(task: "build")

		then:
		result.task(":build").outcome == SUCCESS
	}
}
