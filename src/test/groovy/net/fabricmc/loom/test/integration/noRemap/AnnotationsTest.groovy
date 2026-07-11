/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2026 FabricMC
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

import java.nio.charset.StandardCharsets
import java.nio.file.Path

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import spock.lang.Specification

import net.fabricmc.loom.test.util.GradleProjectTestTrait
import net.fabricmc.loom.util.Checksum
import net.fabricmc.loom.util.ZipUtils

import static net.fabricmc.loom.test.LoomTestConstants.PRE_RELEASE_GRADLE
import static org.gradle.testkit.runner.TaskOutcome.NO_SOURCE
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class AnnotationsTest extends Specification implements GradleProjectTestTrait {
	private static final String MINECRAFT_VERSION = "25w45a_unobfuscated"

	def "javadoc, unpick, constants and annotation patches"() {
		setup:
		def gradle = gradleProject(project: "minimalBaseNoRemap", version: PRE_RELEASE_GRADLE)
		Path annotationsJar = createFixtureRepository(gradle)
		String minecraftArtifactVersion = getMinecraftArtifactVersion(annotationsJar)
		gradle.buildGradle << """
			repositories {
				maven {
					url = uri("repo")
				}
			}

			dependencies {
				minecraft "com.mojang:minecraft:${MINECRAFT_VERSION}"
				annotations "test:annotations:1.0"
			}
		"""

		def sourceFile = new File(gradle.projectDir, "src/main/java/test/Test.java")
		sourceFile.parentFile.mkdirs()
		sourceFile.text = """
			package test;

			public class Test {
				public static final int EXAMPLE_INT = Constants.EXAMPLE_INT;
			}
		"""

		when:
		def compileResult = gradle.run(task: "compileJava")
		def annotationsCache = getAnnotationsCache(gradle, annotationsJar)

		then:
		compileResult.task(":compileJava").outcome == SUCCESS
		new File(gradle.projectDir, "build/classes/java/main/test/Test.class").isFile()
		hasClassAnnotation(gradle.getGeneratedMinecraft(minecraftArtifactVersion, "merged-deobf"), "net/minecraft/client/Options.class", "Ltest/AnnotationPatchApplied;")
		!annotationsCache.exists()
		!new File(gradle.projectDir, ".gradle/loom-cache/source_mappings").exists()

		when:
		def sourcesResult = gradle.run(tasks: [
			"genSourcesWithVineflower",
			"--no-use-cache"
		])
		def optionsSource = getClassSource(gradle, minecraftArtifactVersion, "net/minecraft/client/Options.java")

		then:
		sourcesResult.task(":genSourcesWithVineflower").outcome == SUCCESS
		!annotationsCache.exists()
		optionsSource.contains("/// # Loom test class javadoc")
		optionsSource.contains("/// **Loom test field javadoc**")
		optionsSource.contains("/// `Loom test method javadoc`")
		optionsSource.contains("Constants.EXAMPLE_INT")
	}

	def "annotation patches in split jars"() {
		setup:
		def gradle = gradleProject(project: "minimalBaseNoRemap", version: PRE_RELEASE_GRADLE)
		Path annotationsJar = createFixtureRepository(gradle)
		String minecraftArtifactVersion = getMinecraftArtifactVersion(annotationsJar)
		gradle.buildGradle << """
			loom {
				splitEnvironmentSourceSets()
			}

			repositories {
				maven {
					url = uri("repo")
				}
			}

			dependencies {
				minecraft "com.mojang:minecraft:${MINECRAFT_VERSION}"
				annotations "test:annotations:1.0"
			}
		"""

		when:
		def result = gradle.run(task: "compileJava")

		then:
		result.task(":compileJava").outcome == NO_SOURCE
		hasClassAnnotation(gradle.getGeneratedMinecraft(minecraftArtifactVersion, "clientonly-deobf"), "net/minecraft/client/Options.class", "Ltest/AnnotationPatchApplied;")
		hasClassAnnotation(gradle.getGeneratedMinecraft(minecraftArtifactVersion, "common-deobf"), "net/minecraft/resources/Identifier.class", "Ltest/AnnotationPatchApplied;")
	}

	private static File getAnnotationsCache(GradleProject gradle, Path annotationsJar) {
		return new File(gradle.gradleHomeDir, "caches/fabric-loom/${MINECRAFT_VERSION}/${getAnnotationsIdentifier(annotationsJar)}")
	}

	private static String getMinecraftArtifactVersion(Path annotationsJar) {
		return "${MINECRAFT_VERSION}-${getAnnotationsIdentifier(annotationsJar)}"
	}

	private static String getAnnotationsIdentifier(Path annotationsJar) {
		String hash = Checksum.of(annotationsJar).sha256().hex(12)
		return "annotations.test.annotations.${hash}.${MINECRAFT_VERSION}.1.0-v2"
	}

	private static Path createFixtureRepository(GradleProject gradle) {
		Path annotationsJar = createArtifact(gradle, "annotations")
		ZipUtils.add(annotationsJar, "mappings/mappings.tiny", MAPPINGS)
		ZipUtils.add(annotationsJar, "extras/annotations.json", ANNOTATIONS_PATCH)
		ZipUtils.add(annotationsJar, "extras/definitions.unpick", UNPICK_DEFINITIONS)
		ZipUtils.add(annotationsJar, "extras/unpick.json", UNPICK_METADATA)

		Path constantsJar = createArtifact(gradle, "constants")
		ZipUtils.add(constantsJar, "test/Constants.class", createConstantsClass())
		return annotationsJar
	}

	private static Path createArtifact(GradleProject gradle, String name) {
		File artifactDirectory = new File(gradle.projectDir, "repo/test/${name}/1.0")
		artifactDirectory.mkdirs()

		new File(artifactDirectory, "${name}-1.0.pom").text = """
			<project xmlns="http://maven.apache.org/POM/4.0.0">
				<modelVersion>4.0.0</modelVersion>
				<groupId>test</groupId>
				<artifactId>${name}</artifactId>
				<version>1.0</version>
			</project>
		"""

		return new File(artifactDirectory, "${name}-1.0.jar").toPath()
	}

	private static byte[] createConstantsClass() {
		def writer = new ClassWriter(0)
		writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER, "test/Constants", null, "java/lang/Object", null)
		writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "EXAMPLE_INT", "I", null, 260).visitEnd()
		writer.visitEnd()
		return writer.toByteArray()
	}

	private static boolean hasClassAnnotation(File jar, String className, String annotationDescriptor) {
		def classNode = new ClassNode()
		new ClassReader(ZipUtils.unpack(jar.toPath(), className)).accept(classNode, ClassReader.SKIP_CODE)
		return classNode.invisibleAnnotations.any { it.desc == annotationDescriptor }
	}

	private static String getClassSource(GradleProject gradle, String minecraftArtifactVersion, String className) {
		File sourcesJar = gradle.getGeneratedSources(minecraftArtifactVersion, "merged-deobf")
		return new String(ZipUtils.unpack(sourcesJar.toPath(), className), StandardCharsets.UTF_8)
	}

	private static final String MAPPINGS = "tiny\t2\t0\tofficial\n" +
	"c\tnet/minecraft/client/Options\n" +
	"\tc\t# Loom test class javadoc\n" +
	"\tf\tI\tUNLIMITED_FRAMERATE_CUTOFF\n" +
	"\t\tc\t**Loom test field javadoc**\n" +
	"\tm\t()Lnet/minecraft/client/OptionInstance;\tframerateLimit\n" +
	"\t\tc\t`Loom test method javadoc`\n"

	private static final String ANNOTATIONS_PATCH = '''{
		"version": 1,
		"namespace": "official",
		"classes": {
			"net/minecraft/client/Options": {
				"add": [
					{
						"desc": "Ltest/AnnotationPatchApplied;"
					}
				]
			},
			"net/minecraft/resources/Identifier": {
				"add": [
					{
						"desc": "Ltest/AnnotationPatchApplied;"
					}
				]
			}
		}
	}'''

	private static final String UNPICK_DEFINITIONS = "unpick v4\n\n" +
	"group int example_int\n" +
	"\ttest.Constants.EXAMPLE_INT\n\n" +
	"target_method com.mojang.serialization.Codec intRange (II)Lcom/mojang/serialization/Codec;\n" +
	"\tparam 1 example_int\n"

	private static final String UNPICK_METADATA = """\
		{
			"version": 2,
			"namespace": "official",
			"constants": "test:constants:1.0"
		}
	""".stripIndent()
}
