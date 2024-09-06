/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021-2024 FabricMC
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

import spock.lang.Specification
import spock.lang.Unroll

import net.fabricmc.loom.test.util.GradleProjectTestTrait

import static net.fabricmc.loom.test.LoomTestConstants.STANDARD_TEST_VERSIONS
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class MigrateMappingsTest extends Specification implements GradleProjectTestTrait {
	@Unroll
	def "Migrate mappings yarn short hand (gradle #version)"() {
		setup:
		def gradle = gradleProject(project: "minimalBase", version: version)
		gradle.buildGradle << """
            dependencies {
                minecraft 'com.mojang:minecraft:24w36a'
                mappings 'net.fabricmc:yarn:24w36a+build.2:v2'
            }
            """.stripIndent()

		def sourceFile = new File(gradle.projectDir, "src/main/java/example/Test.java")
		sourceFile.parentFile.mkdirs()
		sourceFile.text = """
		package example;

		import net.minecraft.class_10184;

		public class Test {
			public static void main(String[] args) {
			    class_10184 cls = null;
			}
		}
		"""

		when:
		def result = gradle.run(tasks: [
			"migrateMappings",
			"--mappings",
			"24w36a+build.6"
		])
		def remapped = new File(gradle.projectDir, "remappedSrc/example/Test.java").text

		then:
		result.task(":migrateMappings").outcome == SUCCESS
		remapped.contains("import net.minecraft.predicate.entity.InputPredicate;")
		remapped.contains("InputPredicate cls = null;")

		where:
		version << STANDARD_TEST_VERSIONS
	}

	@Unroll
	def "Migrate mappings maven complete (gradle #version)"() {
		setup:
		def gradle = gradleProject(project: "minimalBase", version: version)
		gradle.buildGradle << """
            dependencies {
                minecraft 'com.mojang:minecraft:24w36a'
                mappings 'net.fabricmc:yarn:24w36a+build.2:v2'
            }
            """.stripIndent()

		def sourceFile = new File(gradle.projectDir, "src/main/java/example/Test.java")
		sourceFile.parentFile.mkdirs()
		sourceFile.text = """
		package example;

		import net.minecraft.class_10184;

		public class Test {
			public static void main(String[] args) {
			    class_10184 cls = null;
			}
		}
		"""

		when:
		def result = gradle.run(tasks: [
			"migrateMappings",
			"--mappings",
			"net.fabricmc:yarn:24w36a+build.6:v2"
		])
		def remapped = new File(gradle.projectDir, "remappedSrc/example/Test.java").text

		then:
		result.task(":migrateMappings").outcome == SUCCESS
		remapped.contains("import net.minecraft.predicate.entity.InputPredicate;")
		remapped.contains("InputPredicate cls = null;")

		where:
		version << STANDARD_TEST_VERSIONS
	}

	@Unroll
	def "Migrate mappings to mojmap (gradle #version)"() {
		setup:
		def gradle = gradleProject(project: "minimalBase", version: version)
		gradle.buildGradle << """
            dependencies {
                minecraft 'com.mojang:minecraft:24w36a'
                mappings 'net.fabricmc:yarn:24w36a+build.6:v2'
            }
            """.stripIndent()

		def sourceFile = new File(gradle.projectDir, "src/main/java/example/Test.java")
		sourceFile.parentFile.mkdirs()
		sourceFile.text = """
		package example;

		import net.minecraft.predicate.entity.InputPredicate;

		public class Test {
			public static void main(String[] args) {
			    InputPredicate cls = null;
			}
		}
		"""

		when:
		def result = gradle.run(tasks: [
			"migrateMappings",
			"--mappings",
			"net.minecraft:mappings:24w36a"
		])
		def remapped = new File(gradle.projectDir, "remappedSrc/example/Test.java").text

		then:
		result.task(":migrateMappings").outcome == SUCCESS
		remapped.contains("import net.minecraft.advancements.critereon.InputPredicate;")

		where:
		version << STANDARD_TEST_VERSIONS
	}
}
