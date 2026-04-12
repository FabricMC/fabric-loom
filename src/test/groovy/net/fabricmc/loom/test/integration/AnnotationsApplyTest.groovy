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

import java.util.function.Predicate

import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import spock.lang.Specification
import spock.lang.Unroll

import net.fabricmc.loom.test.util.GradleProjectTestTrait
import net.fabricmc.loom.util.ZipUtils

import static net.fabricmc.loom.test.LoomTestConstants.STANDARD_TEST_VERSIONS
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class AnnotationsApplyTest extends Specification implements GradleProjectTestTrait {
	@Unroll
	def "annotations apply (gradle #version)"() {
		setup:
		def gradle = gradleProject(project: "annotationsApply", version: version)

		when:
		def result = gradle.run(task: "build")

		then:
		result.task(":build").outcome == SUCCESS
		checkClass(gradle, "net/minecraft/util/math/BlockPos") {
			MethodNode method = it.methods.find {
				it.name == "add" && it.desc == "(Lnet/minecraft/util/math/Vec3i;)Lnet/minecraft/util/math/BlockPos;"
			}

			if (method == null || method.invisibleAnnotations == null) {
				return false
			}

			method.invisibleAnnotations.any { it.desc == "Lorg/jetbrains/annotations/Contract;" }
		}

		where:
		version << STANDARD_TEST_VERSIONS
	}

	boolean checkClass(GradleProject gradle, String clazz, Predicate<ClassNode> test) {
		byte[] bytecode = ZipUtils.unpack(gradle.getGeneratedMinecraft("25w42a-net.fabricmc.yarn.25w42a.25w42a+build.9-v2").toPath(), "${clazz}.class")
		ClassReader reader = new ClassReader(bytecode)
		ClassNode node = new ClassNode()
		reader.accept(node, 0)
		return test.test(node)
	}
}
