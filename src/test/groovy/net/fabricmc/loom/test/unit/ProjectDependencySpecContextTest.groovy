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

import java.util.function.Function
import java.util.stream.Stream

import com.google.gson.JsonObject
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

import net.fabricmc.loom.api.RemapConfigurationSettings
import net.fabricmc.loom.configuration.processors.speccontext.DebofConfiguration
import net.fabricmc.loom.configuration.processors.speccontext.DeobfProjectView
import net.fabricmc.loom.configuration.processors.speccontext.DeobfSpecContext
import net.fabricmc.loom.configuration.processors.speccontext.ProjectView
import net.fabricmc.loom.configuration.processors.speccontext.RemappedProjectView
import net.fabricmc.loom.configuration.processors.speccontext.RemappedSpecContext
import net.fabricmc.loom.util.fmj.FabricModJson
import net.fabricmc.loom.util.fmj.FabricModJsonFactory
import net.fabricmc.loom.util.fmj.FabricModJsonSource

class ProjectDependencySpecContextTest extends Specification {
	def "compile-runtime intersection keeps projects and source sets separate"() {
		given:
		def project = ProjectBuilder.builder().build()
		def view = emptyProjectView(project, remapped)
		def mixed = projectMod("mixed")
		view.getProjectMods(ProjectView.ArtifactUsage.COMPILE, DebofConfiguration.TargetSourceSet.MAIN) >> [
			mixed,
			projectMod("duplicate")
		]
		view.getProjectMods(ProjectView.ArtifactUsage.RUNTIME, DebofConfiguration.TargetSourceSet.MAIN) >> [projectMod("duplicate")]
		view.getProjectMods(ProjectView.ArtifactUsage.RUNTIME, DebofConfiguration.TargetSourceSet.CLIENT) >> [mixed]
		view.areEnvironmentSourceSetsSplit() >> true

		when:
		def context = remapped ? RemappedSpecContext.create(view) : DeobfSpecContext.create(view)

		then:
		context.modDependenciesCompileRuntime().empty

		where:
		remapped << [false, true]
	}

	private ProjectView emptyProjectView(Project project, boolean remapped) {
		if (remapped) {
			return Stub(RemappedProjectView) {
				getRemapConfigurations() >> project.objects.namedDomainObjectList(RemapConfigurationSettings)
				resolveArtifacts(_) >> ({ settings -> Stream.empty() } as Function)
			}
		}

		return Stub(DeobfProjectView) {
			getDependencies(_, _) >> project.files()
		}
	}

	private FabricModJson projectMod(String id) {
		def json = new JsonObject()
		json.addProperty("schemaVersion", 1)
		json.addProperty("id", id)
		json.addProperty("version", "1.0.0")
		return FabricModJsonFactory.create(json, Stub(FabricModJsonSource))
	}
}
