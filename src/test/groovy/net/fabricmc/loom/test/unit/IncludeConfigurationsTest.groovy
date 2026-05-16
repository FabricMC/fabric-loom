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
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

import net.fabricmc.loom.LoomGradleExtension
import net.fabricmc.loom.configuration.IncludeConfigurations
import net.fabricmc.loom.test.util.GradleTestUtil
import net.fabricmc.loom.util.gradle.SourceSetHelper

import static org.mockito.Mockito.mock
import static org.mockito.Mockito.when

class IncludeConfigurationsTest extends Specification {
	private Project createProject(boolean dontRemap = true) {
		def project = ProjectBuilder.builder().build()
		project.plugins.apply("java-library")

		// Use a real Property here so it can be passed to Property.set(Provider) inside the
		// task wiring — Mockito-mocked Properties don't implement ProviderInternal.
		def uncompress = project.objects.property(Boolean.class).convention(false)
		def extension = mock(LoomGradleExtension.class)
		when(extension.getUncompressNestedJars()).thenReturn(uncompress)
		when(extension.dontRemapOutputs()).thenReturn(dontRemap)
		project.extensions.add(LoomGradleExtension.class, "loom", extension)

		return project
	}

	def "naming helpers — main source set uses bare names"() {
		given:
		def main = GradleTestUtil.mockSourceSet("main")

		expect:
		IncludeConfigurations.getIncludeConfigurationName(main) == "include"
		IncludeConfigurations.getIncludeInternalConfigurationName(main) == "includeInternal"
		IncludeConfigurations.getProcessIncludeJarsTaskName(main) == "processIncludeJars"
		IncludeConfigurations.getRemapJarTaskName(main) == "remapJar"
	}

	def "naming helpers — non-main source set is prefixed"() {
		given:
		def client = GradleTestUtil.mockSourceSet("client")

		expect:
		IncludeConfigurations.getIncludeConfigurationName(client) == "clientInclude"
		IncludeConfigurations.getIncludeInternalConfigurationName(client) == "clientIncludeInternal"
		IncludeConfigurations.getProcessIncludeJarsTaskName(client) == "processClientIncludeJars"
		IncludeConfigurations.getRemapJarTaskName(client) == "clientRemapJar"
	}

	def "setupForSourceSet registers main configurations and task"() {
		given:
		def project = createProject()
		def main = SourceSetHelper.getMainSourceSet(project)

		when:
		IncludeConfigurations.setupForSourceSet(project, main)

		then:
		project.configurations.findByName("include") != null
		project.configurations.findByName("includeInternal") != null
		project.tasks.findByName("processIncludeJars") != null
	}

	def "setupForSourceSet registers per-source-set configurations and task"() {
		given:
		def project = createProject()
		def client = project.extensions.getByType(JavaPluginExtension.class).sourceSets.create("client")

		when:
		IncludeConfigurations.setupForSourceSet(project, client)

		then:
		project.configurations.findByName("clientInclude") != null
		project.configurations.findByName("clientIncludeInternal") != null
		project.tasks.findByName("processClientIncludeJars") != null
	}

	def "setupForSourceSet does not leak per-source-set configs into the main namespace"() {
		given:
		def project = createProject()
		def client = project.extensions.getByType(JavaPluginExtension.class).sourceSets.create("client")

		when:
		IncludeConfigurations.setupForSourceSet(project, client)

		then:
		project.configurations.findByName("include") == null
		project.configurations.findByName("includeInternal") == null
		project.tasks.findByName("processIncludeJars") == null
	}

	def "include dependencies are non-transitive when resolved through internal"() {
		given:
		def project = createProject()
		def main = SourceSetHelper.getMainSourceSet(project)
		IncludeConfigurations.setupForSourceSet(project, main)

		project.repositories.mavenCentral()
		project.dependencies.add("include", "org.apache.logging.log4j:log4j-core:2.22.0")

		when:
		def internal = project.configurations.getByName("includeInternal")
		def resolved = internal.incoming.dependencies

		then:
		// log4j-core is the only declared dependency; transitives (e.g. log4j-api) are stripped
		// by the include → includeInternal copy.
		resolved.size() == 1
		resolved.first().name == "log4j-core"
		!(resolved.first() as org.gradle.api.artifacts.ModuleDependency).isTransitive()
	}
}
