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

import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactView;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.attributes.Usage;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.fmj.FabricModJson;
import net.fabricmc.loom.util.fmj.FabricModJsonHelpers;
import net.fabricmc.loom.util.gradle.GradleUtils;

// Used to abstract out the Gradle API usage to ease unit testing.
public interface ProjectView {
	List<FabricModJson> getProjectMods(ArtifactUsage usage, DebofConfiguration.TargetSourceSet target);

	// Returns the mods defined in the current project
	List<FabricModJson> getMods();

	boolean disableProjectDependantMods();

	boolean areEnvironmentSourceSetsSplit();

	String getMinecraftClientSourceSetName();

	static void excludeProjectComponents(ArtifactView.ViewConfiguration view) {
		view.componentFilter(component -> !(component instanceof ProjectComponentIdentifier));
	}

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
		private final ModMetadataResolver metadataResolver;

		protected AbstractProjectView(Project project) {
			this.project = project;
			this.extension = LoomGradleExtension.get(project);
			this.metadataResolver = new ModMetadataResolver(project);
		}

		@Override
		public List<FabricModJson> getProjectMods(ArtifactUsage usage, DebofConfiguration.TargetSourceSet target) {
			Configuration configuration;

			if (extension.disableObfuscation()) {
				DebofConfiguration debofConfiguration = switch (usage) {
				case COMPILE -> DebofConfiguration.COMPILE;
				case RUNTIME -> DebofConfiguration.RUNTIME;
				};

				configuration = debofConfiguration.getConfiguration(project, target);
			} else {
				var sourceSet = target.getSourceSet(project);

				String configurationName = switch (usage) {
				case COMPILE -> sourceSet.getCompileClasspathConfigurationName();
				case RUNTIME -> sourceSet.getRuntimeClasspathConfigurationName();
				};

				configuration = project.getConfigurations().getByName(configurationName);
			}

			return metadataResolver.resolve(configuration);
		}

		@Override
		public List<FabricModJson> getMods() {
			return FabricModJsonHelpers.getModsInProject(project);
		}

		@Override
		public boolean disableProjectDependantMods() {
			return GradleUtils.getBooleanProperty(project, Constants.Properties.DISABLE_PROJECT_DEPENDENT_MODS);
		}

		@Override
		public String getMinecraftClientSourceSetName() {
			return extension.getMinecraftClientSourceSetName();
		}

		@Override
		public boolean areEnvironmentSourceSetsSplit() {
			return extension.areEnvironmentSourceSetsSplit();
		}
	}
}
