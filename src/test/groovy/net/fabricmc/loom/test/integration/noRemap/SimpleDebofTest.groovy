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

import org.intellij.lang.annotations.Language
import spock.lang.Specification
import spock.lang.Unroll

import net.fabricmc.loom.test.util.GradleProjectTestTrait

import static net.fabricmc.loom.test.LoomTestConstants.PRE_RELEASE_GRADLE
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class SimpleDebofTest extends Specification implements GradleProjectTestTrait {
	@Unroll
	def "build"() {
		setup:
		def gradle = gradleProject(project: "minimalBaseNoRemap", version: PRE_RELEASE_GRADLE)
		gradle.buildGradle << '''
				dependencies {
					minecraft 'com.mojang:minecraft:25w45a_unobfuscated'
                }
		'''
		def sourceFile = new File(gradle.projectDir, "src/main/java/example/Test.java")
		sourceFile.parentFile.mkdirs()
		@Language("JAVA") String src =  """
		package example;

		import net.minecraft.resources.Identifier;

		public class Test {
			public static void main(String[] args) {
			    Identifier id = Identifier.fromNamespaceAndPath("loom", "test");
			}
		}
		"""
		sourceFile.text = src

		when:
		def result = gradle.run(task: "build")

		then:
		result.task(":build").outcome == SUCCESS
	}
}
