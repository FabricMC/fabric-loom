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

import org.gradle.api.Project
import org.gradle.jvm.tasks.Jar
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

import net.fabricmc.loom.LoomGradleExtension
import net.fabricmc.loom.configuration.IncludeConfigurations

import static org.mockito.Mockito.mock
import static org.mockito.Mockito.when

class IncludeConfigurationsTest extends Specification {
	private Project createProject() {
		def project = ProjectBuilder.builder().build()
		project.plugins.apply("java-library")

		def uncompress = project.objects.property(Boolean.class).convention(false)
		def extension = mock(LoomGradleExtension.class)
		when(extension.getUncompressNestedJars()).thenReturn(uncompress)
		project.extensions.add(LoomGradleExtension.class, "loom", extension)

		return project
	}

	def "nestJars attaches a custom configuration to a jar task"() {
		given:
		def project = createProject()
		def customInclude = project.configurations.create("customInclude") {
			canBeConsumed = false
			canBeResolved = false
		}

		when:
		IncludeConfigurations.nestJars(project, project.tasks.named("jar", Jar.class), customInclude)

		then:
		def processTask = project.tasks.findByName("processJarCustomIncludeJars")
		processTask != null

		def jar = project.tasks.named("jar", Jar.class).get()
		jar.taskDependencies.getDependencies(jar).contains(processTask)
	}
}
