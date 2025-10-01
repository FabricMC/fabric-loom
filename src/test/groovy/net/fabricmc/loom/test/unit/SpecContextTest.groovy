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

import java.nio.file.Path
import java.util.function.Function
import java.util.stream.Stream

import groovy.transform.CompileStatic
import org.gradle.api.NamedDomainObjectList
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import spock.lang.Specification
import spock.lang.TempDir

import net.fabricmc.loom.LoomGradleExtension
import net.fabricmc.loom.api.RemapConfigurationSettings
import net.fabricmc.loom.api.fmj.FabricModJsonV1Spec
import net.fabricmc.loom.configuration.processors.SpecContextImpl
import net.fabricmc.loom.configuration.processors.SpecContextProjectView
import net.fabricmc.loom.test.util.GradleTestUtil
import net.fabricmc.loom.util.ZipUtils
import net.fabricmc.loom.util.fmj.gen.FabricModJsonV1Generator

import static org.mockito.Mockito.mock
import static org.mockito.Mockito.when

@SuppressWarnings('ExplicitCallToModMethod')
class SpecContextTest extends Specification {
	@TempDir
	Path tempDir

	Project project
	LoomGradleExtension extension
	SpecContextProjectView projectView
	NamedDomainObjectList<RemapConfigurationSettings> remapConfigurations

	RemapConfigurationSettings implementation
	RemapConfigurationSettings runtimeOnly
	RemapConfigurationSettings compileOnly

	Map<RemapConfigurationSettings, List<Path>> runtimeArtifacts = [:]
	Map<RemapConfigurationSettings, List<Path>> apiArtifacts = [:]

	void setup() {
		project = GradleTestUtil.mockProject()
		extension = LoomGradleExtension.get(project)
		projectView = mock(SpecContextProjectView.class)
		remapConfigurations = project.getObjects().namedDomainObjectList(RemapConfigurationSettings.class)

		when(projectView.extension()).thenReturn(extension)
		when(extension.getRemapConfigurations()).thenReturn(remapConfigurations)
		when(projectView.resolveArtifacts(true)).thenReturn(resolve(runtimeArtifacts))
		when(projectView.resolveArtifacts(false)).thenReturn(resolve(apiArtifacts))

		implementation = createConfigurationSettings("implementation")
		runtimeOnly = createConfigurationSettings("runtimeOnly")
		compileOnly = createConfigurationSettings("compileOnly")
		remapConfigurations.addAll([
			implementation,
			runtimeOnly,
			compileOnly
		])

		when(extension.getCompileRemapConfigurations()).thenReturn([implementation, compileOnly])
		when(extension.getRuntimeRemapConfigurations()).thenReturn([implementation, runtimeOnly])
	}

	def "Empty"() {
		setup:
		dependencies(
				implementation: [],
				runtimeOnly: [],
				compileOnly: []
				)

		when:
		def specContext = SpecContextImpl.create(projectView)

		then:
		specContext.modDependencies().size() == 0
		specContext.localMods().size() == 0
		specContext.modDependenciesCompileRuntime().size() == 0
		specContext.modDependenciesCompileRuntimeClient().size() == 0
		specContext.allMods().size() == 0
	}

	def "implementation dependency"() {
		setup:
		dependencies(
				implementation: [mod("test1")],
				runtimeOnly: [],
				compileOnly: []
				)

		when:
		def specContext = SpecContextImpl.create(projectView)

		then:
		specContext.modDependencies().size() == 1
		specContext.localMods().size() == 0
		specContext.modDependenciesCompileRuntime().size() == 1
		specContext.modDependenciesCompileRuntimeClient().size() == 0
		specContext.allMods().size() == 1
	}

	def "runtime only dependency"() {
		setup:
		dependencies(
				implementation: [],
				runtimeOnly: [mod("test1")],
				compileOnly: []
				)

		when:
		def specContext = SpecContextImpl.create(projectView)

		then:
		specContext.modDependencies().size() == 1
		specContext.localMods().size() == 0
		specContext.modDependenciesCompileRuntime().size() == 0
		specContext.modDependenciesCompileRuntimeClient().size() == 0
		specContext.allMods().size() == 1
	}

	def "compile only dependency"() {
		setup:
		dependencies(
				implementation: [],
				runtimeOnly: [],
				compileOnly: [mod("test1")]
				)

		when:
		def specContext = SpecContextImpl.create(projectView)

		then:
		specContext.modDependencies().size() == 1
		specContext.localMods().size() == 0
		specContext.modDependenciesCompileRuntime().size() == 0
		specContext.modDependenciesCompileRuntimeClient().size() == 0
		specContext.allMods().size() == 1
	}

	// TODO I believe this test is testing broken behaviour
	def "compile only runtime only dependency"() {
		setup:
		def test1 = mod("test1")
		dependencies(
				implementation: [],
				runtimeOnly: [test1],
				compileOnly: [test1]
				)

		when:
		def specContext = SpecContextImpl.create(projectView)

		then:
		specContext.modDependencies().size() == 2
		specContext.localMods().size() == 0
		specContext.modDependenciesCompileRuntime().size() == 0
		specContext.modDependenciesCompileRuntimeClient().size() == 0
		specContext.allMods().size() == 2
	}

	private void dependencies(Map<Object, List<Path>> files) {
		configureDependencies(files.implementation, this.implementation)
		configureDependencies(files.runtimeOnly, this.runtimeOnly)
		configureDependencies(files.compileOnly, this.compileOnly)

		runtimeArtifacts[this.implementation].addAll(files.implementation)
		runtimeArtifacts[this.runtimeOnly].addAll(files.runtimeOnly)
		apiArtifacts[this.implementation].addAll(files.implementation)
		apiArtifacts[this.compileOnly].addAll(files.compileOnly)
	}

	private static void configureDependencies(List<Path> files, RemapConfigurationSettings settings) {
		def configuration = mock(Configuration.class)
		when(configuration.resolve()).thenReturn(files*.toFile() as Set)

		def provider = mock(NamedDomainObjectProvider.class)
		when(provider.get()).thenReturn(configuration)

		when(settings.getSourceConfiguration()).thenReturn(provider)
	}

	private Path mod(String modId) {
		def zip = tempDir.resolve("${modId}.zip")

		def spec = project.objects.newInstance(FabricModJsonV1Spec.class)
		spec.modId.set(modId)
		spec.version.set("1.0.0")
		def json = FabricModJsonV1Generator.INSTANCE.generate(spec)
		ZipUtils.add(zip, "fabric.mod.json", json)

		return zip
	}

	private RemapConfigurationSettings createConfigurationSettings(String name) {
		def settings = project.getObjects().newInstance(RemapConfigurationSettings.class, name)
		settings.applyDependencyTransforms.set(true)

		runtimeArtifacts.put(settings, [])
		apiArtifacts.put(settings, [])

		return settings
	}

	@CompileStatic
	private static Function<RemapConfigurationSettings, Stream<Path>> resolve(Map<RemapConfigurationSettings, List<Path>> artifacts) {
		return { settings ->
			def paths = artifacts.get(settings)
			return paths != null ? paths.stream() : Stream.empty()
		}
	}
}
