/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2025 FabricMC
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

import org.objectweb.asm.ClassReader
import org.objectweb.asm.util.Textifier
import org.objectweb.asm.util.TraceClassVisitor
import spock.lang.Specification
import spock.lang.Timeout
import spock.lang.Unroll

import net.fabricmc.loom.test.util.GradleProjectTestTrait
import net.fabricmc.loom.test.util.ServerRunner
import net.fabricmc.loom.util.ZipUtils

import static net.fabricmc.loom.test.LoomTestConstants.PRE_RELEASE_GRADLE
import static net.fabricmc.loom.test.LoomTestConstants.STANDARD_TEST_VERSIONS
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

@Timeout(value = 20, unit = TimeUnit.MINUTES)
class SimpleProjectTest extends Specification implements GradleProjectTestTrait {
	@Unroll
	def "build and run (gradle #version)"() {
		setup:
		def gradle = gradleProject(project: "simple", version: version)
		gradle.buildSrc("remapext") // apply the remap extension plugin

		def server = ServerRunner.create(gradle.projectDir, "1.16.5")
				.withMod(gradle.getOutputFile("fabric-example-mod-1.0.0.jar"))
				.withFabricApi()
		when:
		def result = gradle.run(task: "build")
		def serverResult = server.run()
		then:
		result.task(":build").outcome == SUCCESS
		gradle.getOutputZipEntry("fabric-example-mod-1.0.0.jar", "META-INF/MANIFEST.MF").contains("Fabric-Loom-Version: 0.0.0+unknown")
		gradle.getOutputZipEntry("fabric-example-mod-1.0.0-sources.jar", "net/fabricmc/example/mixin/ExampleMixin.java").contains("class_442") // Very basic test to ensure sources got remapped

		// test same-namespace remapJar tasks
		gradle.getOutputZipEntry("fabric-example-mod-1.0.0-no-remap.jar", "META-INF/MANIFEST.MF").contains("Fabric-Loom-Version: 0.0.0+unknown")
		gradle.getOutputZipEntry("fabric-example-mod-1.0.0-no-remap-sources.jar", "net/fabricmc/example/mixin/ExampleMixin.java").contains("TitleScreen.class") // Very basic test to ensure sources did not get remapped :)

		serverResult.successful()
		serverResult.output.contains("Hello simple Fabric mod") // A check to ensure our mod init was actually called
		serverResult.output.contains("Hello Loom!") // Check that the remapper extension worked
		where:
		version << STANDARD_TEST_VERSIONS
	}

	@Unroll
	def "#ide config generation"() {
		setup:
		def gradle = gradleProject(project: "simple", sharedFiles: true)
		when:
		def result = gradle.run(task: ide)
		then:
		result.task(":${ide}").outcome == SUCCESS
		where:
		ide 				| _
		'ideaSyncTask' 		| _
		'genEclipseRuns'	| _
		'vscode'			| _
	}

	@Unroll
	def "remap mixins with mixin AP"() {
		setup:
		def gradle = gradleProject(project: "simple", version: PRE_RELEASE_GRADLE)
		gradle.buildGradle << """
				allprojects {
					loom.mixin.useLegacyMixinAp = true
				}
				""".stripIndent()

		when:
		def result = gradle.run(task: "build")

		then:
		result.task(":build").outcome == SUCCESS
		!result.output.contains("[WARN]  [MIXIN]") // Assert that tiny remapper didnt not have any warnings when remapping
		gradle.getOutputZipEntry("fabric-example-mod-1.0.0.jar", "META-INF/MANIFEST.MF").contains("Fabric-Loom-Version: 0.0.0+unknown")
	}

	// Tests that deleted files don't remain in built jars after a rebuild.
	// See https://github.com/FabricMC/fabric-loom/issues/1270.
	@Unroll
	def "deleted files disappear from jars (gradle #version)"() {
		// Initial conditions: a project with a resource file to delete.
		setup:
		def gradle = gradleProject(project: "simple", version: version)
		def deletedFile = new File(gradle.projectDir, "src/main/resources/foo.txt")
		deletedFile.text = "hello, world!"
		gradle.run(task: "build")

		when:
		// Delete the resource, then run another build.
		deletedFile.delete()
		def result = gradle.run(task: "build")

		then:
		result.task(":build").outcome == SUCCESS
		!gradle.hasOutputZipEntry("fabric-example-mod-1.0.0.jar", "foo.txt")
		!gradle.hasOutputZipEntry("fabric-example-mod-1.0.0-sources.jar", "foo.txt")
		!gradle.hasOutputZipEntry("fabric-example-mod-1.0.0-no-remap.jar", "foo.txt")
		!gradle.hasOutputZipEntry("fabric-example-mod-1.0.0-no-remap-sources.jar", "foo.txt")

		where:
		version << STANDARD_TEST_VERSIONS
	}

	@Unroll
	def "custom remap mappings (gradle #version)"() {
		// Initial conditions: a project with a resource file to delete.
		setup:
		def gradle = gradleProject(project: "simple", version: version)
		gradle.buildGradle << """
				configurations {
					mojangMappings
				}

				dependencies {
					mojangMappings loom.officialMojangMappings()
				}

				def remapMojmap = tasks.register("remapMojmap", net.fabricmc.loom.task.RemapJarTask) {
					sourceNamespace = "intermediary"
					targetNamespace = "named"
					inputFile = tasks.remapJar.archiveFile
					customMappings.from(configurations.mojangMappings)
					archiveClassifier = "mojmap"

					addNestedDependencies = false // Jars have already been included in the remapJar task
				}

				def remapMojmapSources = tasks.register("remapMojmapSources", net.fabricmc.loom.task.RemapSourcesJarTask) {
					sourceNamespace = "intermediary"
					targetNamespace = "named"
					inputFile = tasks.remapSourcesJar.archiveFile
					customMappings.from(configurations.mojangMappings)
					archiveClassifier = "mojmap-sources"
				}

				// Ensure that the remap classpath has intermediary jars
				for (task in [remapMojmap, remapMojmapSources]) {
					task.configure {
						classpath.setFrom(loom.getMinecraftJars(net.fabricmc.loom.api.mappings.layered.MappingsNamespace.INTERMEDIARY))
						classpath.from(tasks.remapJar.archiveFile)
					}
				}
				"""

		when:
		def result = gradle.run(tasks: [
			"remapMojmap",
			"remapMojmapSources"
		])
		def sourcesJar = gradle.getOutputFile("fabric-example-mod-1.0.0-mojmap-sources.jar").toPath()
		def classesJar = gradle.getOutputFile("fabric-example-mod-1.0.0-mojmap.jar").toPath()

		then:
		result.task(":remapMojmap").outcome == SUCCESS
		result.task(":remapMojmapSources").outcome == SUCCESS

		new String(ZipUtils.unpack(sourcesJar, "/net/fabricmc/example/ExampleMod.java")).contains("ResourceLocation")
		textify(ZipUtils.unpack(classesJar, "/net/fabricmc/example/ExampleMod.class")).contains("ResourceLocation")

		where:
		version << STANDARD_TEST_VERSIONS
	}

	private static String textify(byte[] classData) {
		def stringWriter = new StringWriter()
		def printWriter = new PrintWriter(stringWriter)
		def textifier = new Textifier()
		def traceClassVisitor = new TraceClassVisitor(null, textifier, printWriter)
		def classReader = new ClassReader(classData)
		classReader.accept(traceClassVisitor, 0)
		return stringWriter.toString()
	}
}
