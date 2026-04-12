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

package net.fabricmc.loom.configuration.processors.speccontext;

import java.util.List;
import java.util.stream.Stream;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.attributes.Usage;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.fmj.FabricModJson;
import net.fabricmc.loom.util.fmj.FabricModJsonHelpers;
import net.fabricmc.loom.util.gradle.GradleUtils;

// Used to abstract out the Gradle API usage to ease unit testing.
public interface ProjectView {
	//Returns a list of Loom Projects found in the specified Configuration
	Stream<Project> getLoomProjectDependencies(String name);

	// Returns the mods defined in the current project
	List<FabricModJson> getMods();

	boolean disableProjectDependantMods();

	boolean areEnvironmentSourceSetsSplit();

	enum ArtifactUsage {
		RUNTIME(Usage.JAVA_RUNTIME),
		COMPILE(Usage.JAVA_API);

		private final String gradleUsage;

		ArtifactUsage(String gradleUsage) {
			this.gradleUsage = gradleUsage;
		}

		public String getGradleUsage() {
			return gradleUsage;
		}
	}

	abstract class AbstractProjectView implements ProjectView {
		protected final Project project;
		protected final LoomGradleExtension extension;

		protected AbstractProjectView(Project project) {
			this.project = project;
			this.extension = LoomGradleExtension.get(project);
		}

		@Override
		public Stream<Project> getLoomProjectDependencies(String name) {
			final Configuration configuration = project.getConfigurations().getByName(name);
			return configuration.getAllDependencies()
					.withType(ProjectDependency.class)
					.stream()
					.map((d) -> project.project(d.getPath()))
					.filter(GradleUtils::isLoomProject);
		}

		@Override
		public List<FabricModJson> getMods() {
			return FabricModJsonHelpers.getModsInProject(project);
		}

		@Override
		public boolean disableProjectDependantMods() {
			final LoomGradleExtension extension = LoomGradleExtension.get(project);
			// TODO provide a project isolated way of doing this.
			return extension.isProjectIsolationActive()
					|| GradleUtils.getBooleanProperty(project, Constants.Properties.DISABLE_PROJECT_DEPENDENT_MODS);
		}

		@Override
		public boolean areEnvironmentSourceSetsSplit() {
			return extension.areEnvironmentSourceSetsSplit();
		}
	}
}
