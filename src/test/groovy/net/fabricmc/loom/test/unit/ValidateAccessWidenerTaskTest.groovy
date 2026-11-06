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

package net.fabricmc.loom.test.unit

import java.nio.file.Path

import org.gradle.api.logging.Logger
import org.gradle.testfixtures.ProjectBuilder
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import spock.lang.Specification
import spock.lang.TempDir

import net.fabricmc.loom.LoomGradleExtension
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace
import net.fabricmc.loom.task.ValidateAccessWidenerTask
import net.fabricmc.loom.util.ZipUtils

class ValidateAccessWidenerTaskTest extends Specification {
	@TempDir
	Path tempDir

	ValidateAccessWidenerTask task
	Logger logger = Mock()

	def setup() {
		def writer = new ClassWriter(0)
		writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "example/Target", null, "java/lang/Object", null)
		writer.visitField(Opcodes.ACC_PUBLIC, "x", "I", null, null).visitEnd()
		writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "run", "([Ljava/lang/String;)V", null, null).visitEnd()
		writer.visitEnd()
		def targetJar = tempDir.resolve("target.jar")
		ZipUtils.add(targetJar, "example/Target.class", writer.toByteArray())

		def project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build()
		def accessWidener = project.objects.fileProperty()
		accessWidener.set(tempDir.resolve("test.accesswidener").toFile())
		def extension = Stub(LoomGradleExtension) {
			getAccessWidenerPath() >> accessWidener
			getMinecraftJarsCollection(MappingsNamespace.NAMED) >> project.files(targetJar.toFile())
		}
		project.extensions.add("loom", extension)
		task = project.tasks.register("validateAccessWidener", TestValidateAccessWidenerTask).get()
		task.testLogger = logger
	}

	def "reports invalid references with their line number"() {
		given:
		task.accessWidener.get().asFile.text = "accessWidener v1 named\n" + "\n".repeat(8) + entry

		when:
		task.run()

		then:
		def exception = thrown(RuntimeException)
		exception.message == "access-widener validation failed for test.accesswidener"
		1 * logger.error("{} on line {}", message, 10)
		1 * logger.error("access-widener validation failed for {}", "test.accesswidener")

		where:
		entry | message
		"accessible class example/Missing" | "Could not find class (example/Missing)"
		"accessible field example/Target missing I" | "Could not find field (missing:I) in class (example/Target)"
		"accessible method example/Target run ([Ljava/lang/NotAString;)V" | "Could not find method (run([Ljava/lang/NotAString;)V) in class (example/Target)"
	}

	def "accepts valid class field and method references"() {
		given:
		task.accessWidener.get().asFile.text = """\
			accessWidener v1 named
			accessible class example/Target
			accessible field example/Target x I
			accessible method example/Target run ([Ljava/lang/String;)V
			""".stripIndent()

		when:
		task.run()

		then:
		notThrown(Exception)
		0 * logger.error(*_)
	}

	static abstract class TestValidateAccessWidenerTask extends ValidateAccessWidenerTask {
		Logger testLogger

		@Override
		Logger getLogger() {
			return testLogger ?: super.getLogger()
		}
	}
}
