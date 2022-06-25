/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2021 FabricMC
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

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.LoomRepositoryPlugin;
import net.fabricmc.loom.configuration.mods.ModConfigurationRemapper;
import net.fabricmc.loom.configuration.ide.idea.IdeaUtils;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.SourceRemapper;
import net.fabricmc.loom.util.ZipUtils;

public class LoomDependencyManager {
	public void handleDependencies(Project project) {
		List<Runnable> afterTasks = new ArrayList<>();

		project.getLogger().info(":setting up loom dependencies");
		LoomGradleExtension extension = LoomGradleExtension.get(project);

		if (extension.getInstallerData() == null) {
			//If we've not found the installer JSON we've probably skipped remapping Fabric loader, let's go looking
			project.getLogger().info("Searching through modCompileClasspath for installer JSON");
			final Configuration configuration = project.getConfigurations().getByName(Constants.Configurations.MOD_COMPILE_CLASSPATH);

			for (Dependency dependency : configuration.getAllDependencies()) {
				for (File input : configuration.files(dependency)) {
					JsonObject jsonObject = readInstallerJson(input);

					if (jsonObject != null) {
						if (extension.getInstallerData() != null) {
							project.getLogger().info("Found another installer JSON in, ignoring it! " + input);
							continue;
						}

						project.getLogger().info("Found installer JSON in " + input);
						extension.setInstallerData(new InstallerData(dependency.getVersion(), jsonObject));
						handleInstallerJson(jsonObject, project);
					}
				}
			}
		}

		SourceRemapper sourceRemapper = new SourceRemapper(project, true);
		String mappingsIdentifier = extension.getMappingsProvider().mappingsIdentifier();

		ModConfigurationRemapper.supplyModConfigurations(project, mappingsIdentifier, extension, sourceRemapper);

		sourceRemapper.remapAll();

		if (extension.getInstallerData() == null) {
			project.getLogger().warn("fabric-installer.json not found in classpath!");
		}

		for (Runnable runnable : afterTasks) {
			runnable.run();
		}
	}

	public static JsonObject readInstallerJson(File file) {
		try {
			byte[] bytes = ZipUtils.unpackNullable(file.toPath(), "fabric-installer.json");

			if (bytes == null) {
				return null;
			}

			return LoomGradlePlugin.GSON.fromJson(new String(bytes, StandardCharsets.UTF_8), JsonObject.class);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to try and read installer json from", e);
		}
	}

	private static void handleInstallerJson(JsonObject jsonObject, Project project) {
		LoomGradleExtension extension = LoomGradleExtension.get(project);

		JsonObject libraries = jsonObject.get("libraries").getAsJsonObject();
		Configuration loaderDepsConfig = project.getConfigurations().getByName(Constants.Configurations.LOADER_DEPENDENCIES);
		Configuration apDepsConfig = project.getConfigurations().getByName("annotationProcessor");

		libraries.get("common").getAsJsonArray().forEach(jsonElement -> {
			String name = jsonElement.getAsJsonObject().get("name").getAsString();

			ExternalModuleDependency modDep = (ExternalModuleDependency) project.getDependencies().create(name);
			modDep.setTransitive(false);
			loaderDepsConfig.getDependencies().add(modDep);

			// TODO: work around until https://github.com/FabricMC/Mixin/pull/60 and https://github.com/FabricMC/fabric-mixin-compile-extensions/issues/14 is fixed.
			if (!IdeaUtils.isIdeaSync() && extension.getMixin().getUseLegacyMixinAp().get()) {
				apDepsConfig.getDependencies().add(modDep);
			}

			project.getLogger().debug("Loom adding " + name + " from installer JSON");

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
