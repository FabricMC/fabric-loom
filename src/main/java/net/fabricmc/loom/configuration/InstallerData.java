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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.plugins.JavaPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.LoomRepositoryPlugin;
import net.fabricmc.loom.configuration.ide.idea.IdeaUtils;
import net.fabricmc.loom.util.Constants;

public record InstallerData(String version, JsonObject installerJson) {
	private static final Logger LOGGER = LoggerFactory.getLogger(InstallerData.class);

	public void applyToProject(Project project) {
		LoomGradleExtension extension = LoomGradleExtension.get(project);

		if (extension.getInstallerData() != null) {
			throw new IllegalStateException("Already applied installer data");
		}

		extension.setInstallerData(this);

		final JsonObject libraries = installerJson.get("libraries").getAsJsonObject();

		applyDependendencies(libraries.get("common").getAsJsonArray(), project);

		// Apply development dependencies if they exist.
		if (libraries.has("development")) {
			applyDependendencies(libraries.get("development").getAsJsonArray(), project);
		}
	}

	private void applyDependendencies(JsonArray jsonArray, Project project) {
		LoomGradleExtension extension = LoomGradleExtension.get(project);
		Configuration loaderDepsConfig = project.getConfigurations().getByName(Constants.Configurations.LOADER_DEPENDENCIES);
		Configuration annotationProcessor = project.getConfigurations().getByName(JavaPlugin.ANNOTATION_PROCESSOR_CONFIGURATION_NAME);

		for (JsonElement jsonElement : jsonArray) {
			final JsonObject jsonObject = jsonElement.getAsJsonObject();
			final String name = jsonObject.get("name").getAsString();

			LOGGER.debug("Adding dependency ({}) from installer JSON", name);

			ExternalModuleDependency modDep = (ExternalModuleDependency) project.getDependencies().create(name);
			modDep.setTransitive(false); // Match the launcher in not being transitive
			loaderDepsConfig.getDependencies().add(modDep);

			// Work around https://github.com/FabricMC/Mixin/pull/60 and https://github.com/FabricMC/fabric-mixin-compile-extensions/issues/14.
			if (!IdeaUtils.isIdeaSync() && extension.getMixin().getUseLegacyMixinAp().get()) {
				annotationProcessor.getDependencies().add(modDep);
			}

			// If user choose to use dependencyResolutionManagement, then they should declare
			// these repositories manually in the settings file.
			if (project.getGradle().getPlugins().hasPlugin(LoomRepositoryPlugin.class)) {
				continue;
			}

			addRepository(jsonObject, project);
		}
	}

	private void addRepository(JsonObject jsonObject, Project project) {
		if (!jsonObject.has("url")) {
			return;
		}

		final String url = jsonObject.get("url").getAsString();
		final boolean isPresent = project.getRepositories().stream()
				.filter(artifactRepository -> artifactRepository instanceof MavenArtifactRepository)
				.map(artifactRepository -> (MavenArtifactRepository) artifactRepository)
				.anyMatch(mavenArtifactRepository -> mavenArtifactRepository.getUrl().toString().equalsIgnoreCase(url));

		if (isPresent) {
			return;
		}

		project.getRepositories().maven(mavenArtifactRepository -> mavenArtifactRepository.setUrl(jsonObject.get("url").getAsString()));
	}
}
