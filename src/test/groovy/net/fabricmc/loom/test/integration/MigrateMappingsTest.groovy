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

	@Unroll
	def "Migrate client mappings (gradle #version)"() {
		setup:
		def gradle = gradleProject(project: "minimalBase", version: version)
		gradle.buildGradle << """
            loom {
                splitEnvironmentSourceSets()
            }

            dependencies {
                minecraft 'com.mojang:minecraft:24w36a'
                mappings 'net.fabricmc:yarn:24w36a+build.6:v2'
            }
            """.stripIndent()

		def sourceFile = new File(gradle.projectDir, "src/client/java/example/Test.java")
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
			"migrateClientMappings",
			"--mappings",
			"net.minecraft:mappings:24w36a"
		])
		def remapped = new File(gradle.projectDir, "remappedClientSrc/example/Test.java").text

		then:
		result.task(":migrateClientMappings").outcome == SUCCESS
		remapped.contains("import net.minecraft.advancements.critereon.InputPredicate;")

		where:
		version << STANDARD_TEST_VERSIONS
	}

	@Unroll
	def "Override inputs (gradle #version)"() {
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
			"net.minecraft:mappings:24w36a",
			"--overrideInputsIHaveABackup"
		])
		def remapped = new File(gradle.projectDir, "src/main/java/example/Test.java").text

		then:
		result.task(":migrateMappings").outcome == SUCCESS
		remapped.contains("import net.minecraft.advancements.critereon.InputPredicate;")

		where:
		version << STANDARD_TEST_VERSIONS
	}

	def "Migrate AW (in place: #inPlace, header: #header)"() {
		setup:
		def gradle = gradleProject(project: "minimalBase")
		gradle.buildGradle << """
			loom.accessWidenerPath = file('src/main/resources/test.accesswidener')
            dependencies {
                minecraft 'com.mojang:minecraft:24w36a'
                mappings 'net.fabricmc:yarn:24w36a+build.6:v2'
            }
            """.stripIndent()
		def awFile = new File(gradle.projectDir, 'src/main/resources/test.accesswidener')
		awFile.parentFile.mkdirs()
		awFile.text = header + '\n' + 'extendable method net/minecraft/block/PaneBlock connectsTo (Lnet/minecraft/block/BlockState;Z)Z'

		when:
		def tasks = [
			"migrateClassTweakerMappings",
			"--mappings",
			"net.minecraft:mappings:24w36a"
		]

		if (inPlace) {
			tasks.add("--overrideInputsIHaveABackup")
		}

		def result = gradle.run(tasks: tasks)
		def remapped = (inPlace ? awFile : new File(gradle.projectDir, 'remapped.accesswidener')).text

		then:
		result.task(":migrateClassTweakerMappings").outcome == SUCCESS
		remapped == header + '\n' + 'extendable\tmethod\tnet/minecraft/world/level/block/IronBarsBlock\tattachsTo\t(Lnet/minecraft/world/level/block/state/BlockState;Z)Z\n'

		where:
		// Check that the header is kept intact and that the in place remapping works
		header | inPlace
		'accessWidener\tv1\tnamed' | false
		'accessWidener\tv1\tnamed' | true // the code is the same so we only need one case for in place remapping
		'accessWidener\tv2\tnamed' | false
		'classTweaker\tv1\tnamed'  | false
	}
}
