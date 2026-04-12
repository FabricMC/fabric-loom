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

package net.fabricmc.loom.test.integration.noRemap

import java.nio.file.Path

import org.intellij.lang.annotations.Language
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

import net.fabricmc.loom.test.util.GradleProjectTestTrait
import net.fabricmc.loom.test.util.MockMavenServerTrait
import net.fabricmc.loom.util.ZipUtils

import static net.fabricmc.loom.test.LoomTestConstants.PRE_RELEASE_GRADLE
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class DebofDependenciesTest extends Specification implements GradleProjectTestTrait, MockMavenServerTrait {
	@TempDir
	Path tempDir

	@Unroll
	def "apply interface injection"() {
		setup:
		def dep = tempDir.resolve("mod.jar")
		ZipUtils.add(dep, "fabric.mod.json", FMJ)
		ZipUtils.add(dep, "test.accesswidener", AW)

		mavenHelper("loom.test", "test", "1.0.0").copyToMaven(dep, null)

		def gradle = gradleProject(project: "minimalBaseNoRemap", version: PRE_RELEASE_GRADLE)
		gradle.buildGradle << repositoriesBlock
		gradle.buildGradle << '''
				dependencies {
					minecraft 'com.mojang:minecraft:25w45a_unobfuscated'
					implementation 'loom.test:test:1.0.0'
                }
		'''
		def pkg = new File(gradle.projectDir, "src/main/java/example/")
		pkg.mkdirs()
		new File(pkg, "Test.java").text = Test
		new File(pkg, "InjectedInterface.java").text = InjectedInterface

		when:
		def result = gradle.run(task: "build")

		then:
		result.task(":build").outcome == SUCCESS
	}

	@Language("JSON")
	private static final String FMJ = """
	{
	  "schemaVersion": 1,
	  "id": "testmod",
	  "version": "1",
	  "name": "Test Mod",
	  "accessWidener": "test.accesswidener",
	  "custom": {
		"loom:injected_interfaces": {
		  "net/minecraft/resources/Identifier": ["example/InjectedInterface"]
		}
	  }
	}
	"""

	@Language("Access Widener")
	private static final String AW = """
	accessWidener\tv2\tofficial
	transitive-accessible field net/minecraft/resources/Identifier path Ljava/lang/String;
	""".stripIndent().trim()

	@Language("JAVA")
	private static final String Test = """
		package example;

		import net.minecraft.resources.Identifier;

		public class Test {
			public static void main(String[] args) {
				Identifier id = Identifier.fromNamespaceAndPath("loom", "test");
				id.testCompiles(); // Test iface injection
				String path = id.path; // Test AW
			}
		}
	"""

	@Language("JAVA")
	private static final String InjectedInterface = """
		package example;

		public interface InjectedInterface {
			default void testCompiles() {
			}
		}
	"""
}
