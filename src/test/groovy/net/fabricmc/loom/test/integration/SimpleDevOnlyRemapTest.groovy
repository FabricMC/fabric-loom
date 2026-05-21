/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 FabricMC
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

import java.nio.file.Path

import org.intellij.lang.annotations.Language
import spock.lang.Specification
import spock.lang.Unroll

import net.fabricmc.loom.test.LoomTestVersions
import net.fabricmc.loom.test.util.GradleProjectTestTrait

import static net.fabricmc.loom.test.LoomTestConstants.PRE_RELEASE_GRADLE
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class SimpleDevOnlyRemapTest extends Specification implements GradleProjectTestTrait {
	@Unroll
	def "build"() {
		setup:
		def mappings = Path.of("src/test/resources/mappings/25w46a_unobfuscated-intermediary-minimal.tiny").toAbsolutePath()
		def gradle = gradleProject(project: "minimalBase", version: PRE_RELEASE_GRADLE)
		gradle.buildGradle << """
                loom {
                    useIntermediateMappings = true
                }

				dependencies {
					minecraft 'com.mojang:minecraft:25w46a_unobfuscated'
					mappings 'net.fabricmc:yarn:25w46a+build.2:v2'
					modImplementation "${LoomTestVersions.FABRIC_LOADER.mavenNotation()}"
                }
		"""
		gradle.buildSrc("devOnlyRemapIntermediary")
		def sourceFile = new File(gradle.projectDir, "src/main/java/example/Test.java")
		sourceFile.parentFile.mkdirs()
		@Language("JAVA") String src =  """
		package example;

		import net.minecraft.util.Identifier;

		import org.spongepowered.asm.mixin.Mixin; // Make sure we applied loaders deps via the installer data

		public class Test {
			public static void main(String[] args) {
			    Identifier id = Identifier.of("loom", "test");
			}
		}
		"""
		sourceFile.text = src

		when:
		def result = gradle.run(
				tasks: [
					"build",
					"configureClientLaunch"
				],
				args: [
					"-Ploom.test.devOnlyRemapIntermediary.mappingPath=${mappings}"
				]
				)

		then:
		result.task(":build").outcome == SUCCESS
		result.task(":configureClientLaunch").outcome == SUCCESS
	}

	@Unroll
	def "split build"() {
		setup:
		def mappings = Path.of("src/test/resources/mappings/25w46a_unobfuscated-intermediary-minimal.tiny").toAbsolutePath()
		def gradle = gradleProject(project: "minimalBase", version: PRE_RELEASE_GRADLE)
		gradle.buildGradle << """
				loom {
					splitEnvironmentSourceSets()
					useIntermediateMappings = true
				}

				dependencies {
					minecraft 'com.mojang:minecraft:25w46a_unobfuscated'
					mappings 'net.fabricmc:yarn:25w46a+build.2:v2'
					modImplementation "${LoomTestVersions.FABRIC_LOADER.mavenNotation()}"
                }
		"""
		gradle.buildSrc("devOnlyRemapIntermediary")
		def sourceFile = new File(gradle.projectDir, "src/main/java/example/Test.java")
		sourceFile.parentFile.mkdirs()
		@Language("JAVA") String src =  """
		package example;

		import net.minecraft.util.Identifier;

		import org.spongepowered.asm.mixin.Mixin; // Make sure we applied loaders deps via the installer data

		public class Test {
			public static void main(String[] args) {
			    Identifier id = Identifier.of("loom", "test");
			}
		}
		"""
		sourceFile.text = src

		when:
		def result = gradle.run(
				task: "build",
				args: [
					"-Ploom.test.devOnlyRemapIntermediary.mappingPath=${mappings}"
				]
				)

		then:
		result.task(":build").outcome == SUCCESS
	}

	@Unroll
	def "build with official to named"() {
		setup:
		def mappings = Path.of("src/test/resources/mappings/25w46a_unobfuscated-named-minimal.tiny").toAbsolutePath()
		def gradle = gradleProject(project: "minimalBase", version: PRE_RELEASE_GRADLE)
		gradle.buildGradle << """
				dependencies {
					minecraft 'com.mojang:minecraft:25w46a_unobfuscated'
					mappings loom.layered {
					    it.mappings file("${mappings}")
					}
					modImplementation "${LoomTestVersions.FABRIC_LOADER.mavenNotation()}"
                }

                loom {
                    noIntermediateMappings()
                }
		"""
		def sourceFile = new File(gradle.projectDir, "src/main/java/example/Test.java")
		sourceFile.parentFile.mkdirs()
		@Language("JAVA") String src =  """
		package example;

		import net.minecraft.util.Identifier;

		import org.spongepowered.asm.mixin.Mixin; // Make sure we applied loaders deps via the installer data

		public class Test {
			public static void main(String[] args) {
			    Identifier id = Identifier.of("loom", "test");
			}
		}
		"""
		sourceFile.text = src

		when:
		def result = gradle.run(
				tasks: [
					"build",
					"configureClientLaunch"
				],
				args: [
					"-Ploom.test.devOnlyRemapIntermediary.mappingPath=${mappings}"
				]
				)

		then:
		result.task(":build").outcome == SUCCESS
		result.task(":configureClientLaunch").outcome == SUCCESS
	}
}
