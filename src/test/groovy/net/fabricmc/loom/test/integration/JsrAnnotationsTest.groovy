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

import java.nio.charset.StandardCharsets

import spock.lang.Specification

import net.fabricmc.loom.test.util.GradleProjectTestTrait
import net.fabricmc.loom.util.ZipUtils

import static net.fabricmc.loom.test.LoomTestConstants.PRE_RELEASE_GRADLE
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class JsrAnnotationsTest extends Specification implements GradleProjectTestTrait {
	static final String MAPPINGS = "21w13a-net.fabricmc.yarn.21w13a.21w13a+build.30-v2"
	static final boolean REMAP_JSR_DEFAULT = true

	def "Remap JSR annotations to JetBrains #remapJsrAnnotations"() {
		setup:
		def gradle = gradleProject(project: "unpick", version: PRE_RELEASE_GRADLE)
		gradle.buildGradle << """
            loom {
                remapJsrAnnotationsToJetBrains = ${remapJsrAnnotations}
            }
            """.stripIndent()
		when:
		def result = gradle.run(tasks: [
			"genSourcesWithVineflower",
			"--info"
		])
		def source = getClassSource(gradle, "net/minecraft/util/annotation/MethodsReturnNonnullByDefault.java", remapJsrAnnotations != REMAP_JSR_DEFAULT)

		then:
		result.task(":genSourcesWithVineflower").outcome == SUCCESS
		source.contains(remapJsrAnnotations ? "@NotNull" : "@Nonnull")
		source.contains(remapJsrAnnotations ? "import org.jetbrains.annotations.NotNull;" : "import javax.annotation.Nonnull;")
		!source.contains(remapJsrAnnotations ? "@Nonnull" : "@NotNull")
		!source.contains(remapJsrAnnotations ? "import javax.annotation.Nonnull;" : "import org.jetbrains.annotations.NotNull;")

		where:
		[remapJsrAnnotations] << [[true, false]].combinations()
	}

	private static String getClassSource(GradleProject gradle, String classname, boolean local, String mappings = MAPPINGS) {
		File sourcesJar = local ? gradle.getGeneratedLocalSources(mappings) : gradle.getGeneratedSources(mappings)
		return new String(ZipUtils.unpack(sourcesJar.toPath(), classname), StandardCharsets.UTF_8)
	}
}
