/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021 FabricMC
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

import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

import net.fabricmc.loom.configuration.ide.RunConfigSettings
import net.fabricmc.loom.configuration.ide.RunConfigUtils
import net.fabricmc.loom.util.Arguments

class RunConfigUnitTest extends Specification {
	def "escape arguments"() {
		when:
		def args = Arguments.join([
			"-Dfabric.test=123",
			"-Dfabric.test=abc 123"
		])

		then:
		args == '-Dfabric.test=123 "-Dfabric.test=abc 123"'
	}

	def "server with gui removes nogui argument"() {
		given:
		def project = ProjectBuilder.builder().build()
		def run = project.objects.newInstance(RunConfigSettings, project, "server")
		run.server()
		run.programArguments.addAll("custom", "nogui")

		when:
		run.serverWithGui()

		then:
		run.programArguments.get() == ["custom"]
	}
	def "runtime file arguments include configured mods"() {
		given:
		def project = ProjectBuilder.builder().build()
		def run = project.objects.newInstance(RunConfigSettings, project, "client")
		run.mods.from("mods/first mod.jar", "mods/second.jar")

		when:
		def arguments = RunConfigUtils.getAdditionalGradleJvmArguments(run, project).get()

		then:
		arguments == [
			"-Dfabric.addMods=" + project.file("mods/first mod.jar").absolutePath + File.pathSeparator + project.file("mods/second.jar").absolutePath
		]
	}

	def "runtime files are empty and mixin agent is disabled by default"() {
		given:
		def project = ProjectBuilder.builder().build()
		def run = project.objects.newInstance(RunConfigSettings, project, "client")

		expect:
		!run.mixinAgent.get()
		RunConfigUtils.getAdditionalGradleJvmArguments(run, project).get().empty
	}

	def "runtime file inheritance remains lazy and retains producer dependencies"() {
		given:
		def project = ProjectBuilder.builder().build()
		def producer = project.tasks.register("produceMod") {
			outputs.file(project.layout.buildDirectory.file("mod.jar"))
		}
		def parent = project.objects.newInstance(RunConfigSettings, project, "parent")
		def child = project.objects.newInstance(RunConfigSettings, project, "child")
		child.inherit(parent)
		child.mods.from("child.jar")

		when:
		parent.mods.from(producer)
		parent.mixinAgent.set(true)

		then:
		child.mods.files == [project.file("build/mod.jar"), project.file("child.jar")] as Set
		child.mods.buildDependencies.getDependencies(null) == [producer.get()] as Set
		child.mixinAgent.get()

		when:
		child.mixinAgent.set(false)
		child.mods.disallowChanges()

		then:
		!child.mixinAgent.get()
		child.mods.buildDependencies.getDependencies(null) == [producer.get()] as Set
	}

	def "mixin agent uses the resolved runtime version"() {
		given:
		def project = ProjectBuilder.builder().build()
		project.pluginManager.apply("java")
		def repository = project.file("repository")

		for (String version : ["1.0", "2.0"]) {
			def moduleDirectory = new File(repository, "net/fabricmc/sponge-mixin/" + version)
			moduleDirectory.mkdirs()
			new File(moduleDirectory, "sponge-mixin-" + version + ".pom").text = """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>net.fabricmc</groupId>
                    <artifactId>sponge-mixin</artifactId>
                    <version>${version}</version>
                </project>
            """
			new File(moduleDirectory, "sponge-mixin-" + version + ".jar").bytes = new byte[0]
		}

		project.repositories.maven { url = repository.toURI() }
		project.dependencies.add("implementation", "net.fabricmc:sponge-mixin:1.0")
		project.dependencies.add("runtimeOnly", "net.fabricmc:sponge-mixin:2.0")
		def run = project.objects.newInstance(RunConfigSettings, project, "client")
		run.sourceSet.set("main")
		run.mixinAgent.set(true)
		def mixinJar = new File(repository, "net/fabricmc/sponge-mixin/2.0/sponge-mixin-2.0.jar")

		expect:
		RunConfigUtils.getAdditionalGradleJvmArguments(run, project).get() == ["-javaagent:" + mixinJar.absolutePath]
	}

	def "missing mixin agent gives an actionable error"() {
		given:
		def project = ProjectBuilder.builder().build()
		project.pluginManager.apply("java")
		def run = project.objects.newInstance(RunConfigSettings, project, "client")
		run.sourceSet.set("main")
		run.mixinAgent.set(true)

		when:
		RunConfigUtils.getAdditionalGradleJvmArguments(run, project).get()

		then:
		def exception = thrown(GradleException)
		exception.message == "Expected one Mixin jar on the runtime classpath for run 'client', found 0"
	}

}
