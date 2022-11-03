/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021-2022 FabricMC
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

package net.fabricmc.loom.configuration;

import com.google.gson.JsonObject;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.LoomRepositoryPlugin;
import net.fabricmc.loom.configuration.ide.idea.IdeaUtils;
import net.fabricmc.loom.util.Constants;

public record InstallerData(String version, JsonObject installerJson) {
	public void applyToProject(Project project) {
		LoomGradleExtension extension = LoomGradleExtension.get(project);

		if (extension.getInstallerData() != null) {
			throw new IllegalStateException("Already applied installer data");
		}

		extension.setInstallerData(this);

		JsonObject libraries = installerJson.get("libraries").getAsJsonObject();
		Configuration loaderDepsConfig = project.getConfigurations().getByName(Constants.Configurations.LOADER_DEPENDENCIES);
		Configuration apDepsConfig = project.getConfigurations().getByName("annotationProcessor");

		libraries.get("common").getAsJsonArray().forEach(jsonElement -> {
			String name = jsonElement.getAsJsonObject().get("name").getAsString();
			project.getLogger().debug("Adding dependency ({}) from installer JSON", name);

			ExternalModuleDependency modDep = (ExternalModuleDependency) project.getDependencies().create(name);
			modDep.setTransitive(false);
			loaderDepsConfig.getDependencies().add(modDep);

			// TODO: work around until https://github.com/FabricMC/Mixin/pull/60 and https://github.com/FabricMC/fabric-mixin-compile-extensions/issues/14 is fixed.
			if (!IdeaUtils.isIdeaSync() && extension.getMixin().getUseLegacyMixinAp().get()) {
				apDepsConfig.getDependencies().add(modDep);
			}

			// If user choose to use dependencyResolutionManagement, then they should declare
			// these repositories manually in the settings file.
			if (jsonElement.getAsJsonObject().has("url") && !project.getGradle().getPlugins().hasPlugin(LoomRepositoryPlugin.class)) {
				String url = jsonElement.getAsJsonObject().get("url").getAsString();
				long count = project.getRepositories().stream().filter(artifactRepository -> artifactRepository instanceof MavenArtifactRepository)
						.map(artifactRepository -> (MavenArtifactRepository) artifactRepository)
						.filter(mavenArtifactRepository -> mavenArtifactRepository.getUrl().toString().equalsIgnoreCase(url)).count();

				if (count == 0) {
					project.getRepositories().maven(mavenArtifactRepository -> mavenArtifactRepository.setUrl(jsonElement.getAsJsonObject().get("url").getAsString()));
				}
			}
		});
	}
}
