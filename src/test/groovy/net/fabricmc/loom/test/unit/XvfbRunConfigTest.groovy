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

package net.fabricmc.loom.test.unit

import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

import net.fabricmc.loom.LoomGradlePlugin
import net.fabricmc.loom.configuration.ide.RunConfig
import net.fabricmc.loom.configuration.ide.RunConfigSettings

class XvfbRunConfigTest extends Specification {
	def "useXvfb defaults to false"() {
		given:
		def project = createProject()
		def settings = createRunConfigSettings(project, "client")

		expect:
		!settings.isUseXvfb()
	}

	def "useXvfb can be enabled"() {
		given:
		def project = createProject()
		def settings = createRunConfigSettings(project, "client")

		when:
		settings.useXvfb()

		then:
		settings.isUseXvfb()
	}

	def "useXvfb can be set with boolean parameter"() {
		given:
		def project = createProject()
		def settings = createRunConfigSettings(project, "client")

		when:
		settings.useXvfb(enabled)

		then:
		settings.isUseXvfb() == enabled

		where:
		enabled << [true, false]
	}

	def "useXvfb is inherited from parent config"() {
		given:
		def project = createProject()
		def parent = createRunConfigSettings(project, "parent")
		def child = createRunConfigSettings(project, "child")
		parent.useXvfb(true)

		when:
		child.inherit(parent)

		then:
		child.isUseXvfb()
	}

	def "RunConfig field can be set"() {
		given:
		def runConfig = new RunConfig()

		when:
		runConfig.useXvfb = value

		then:
		runConfig.useXvfb == value

		where:
		value << [true, false]
	}

	private Project createProject() {
		def project = ProjectBuilder.builder().build()
		project.plugins.apply(LoomGradlePlugin)
		return project
	}

	private RunConfigSettings createRunConfigSettings(Project project, String name) {
		// Use Gradle's object factory to create an instance with automatic abstract method implementation
		return project.objects.newInstance(RunConfigSettings, project, name)
	}
}