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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.LoomRepositoryPlugin;
import net.fabricmc.loom.build.ModCompileRemapper;
import net.fabricmc.loom.configuration.DependencyProvider.DependencyInfo;
import net.fabricmc.loom.configuration.ide.intelij.IntelijUtils;
import net.fabricmc.loom.configuration.providers.mappings.MappingsProviderImpl;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.SourceRemapper;
import net.fabricmc.loom.util.ZipUtils;

public class LoomDependencyManager {
	private static class ProviderList {
		private final String key;
		private final List<DependencyProvider> providers = new ArrayList<>();

		ProviderList(String key) {
			this.key = key;
		}
	}

	private final List<DependencyProvider> dependencyProviderList = new ArrayList<>();

	public <T extends DependencyProvider> T addProvider(T provider) {
		if (dependencyProviderList.contains(provider)) {
			throw new RuntimeException("Provider is already registered");
		}

		if (getProvider(provider.getClass()) != null) {
			throw new RuntimeException("Provider of this type is already registered");
		}

		provider.register(this);
		dependencyProviderList.add(provider);
		return provider;
	}

	public <T> T getProvider(Class<T> clazz) {
		for (DependencyProvider provider : dependencyProviderList) {
			if (provider.getClass() == clazz) {
				return (T) provider;
			}
		}

		return null;
	}

	public void handleDependencies(Project project) {
		List<Runnable> afterTasks = new ArrayList<>();

		MappingsProviderImpl mappingsProvider = null;

		project.getLogger().info(":setting up loom dependencies");
		LoomGradleExtension extension = LoomGradleExtension.get(project);
		Map<String, ProviderList> providerListMap = new HashMap<>();
		List<ProviderList> targetProviders = new ArrayList<>();

		for (DependencyProvider provider : dependencyProviderList) {
			providerListMap.computeIfAbsent(provider.getTargetConfig(), (k) -> {
				ProviderList list = new ProviderList(k);
				targetProviders.add(list);
				return list;
			}).providers.add(provider);

			if (provider instanceof MappingsProviderImpl) {
				mappingsProvider = (MappingsProviderImpl) provider;
			}
		}

		if (mappingsProvider == null) {
			throw new RuntimeException("Could not find MappingsProvider instance!");
		}

		for (ProviderList list : targetProviders) {
			Configuration configuration = project.getConfigurations().getByName(list.key);
			DependencySet dependencies = configuration.getDependencies();

			if (dependencies.isEmpty()) {
				throw new IllegalArgumentException(String.format("No '%s' dependency was specified!", list.key));
			}

			if (dependencies.size() > 1) {
				throw new IllegalArgumentException(String.format("Only one '%s' dependency should be specified, but %d were!",
												list.key,
												dependencies.size())
				);
			}

			for (Dependency dependency : dependencies) {
				for (DependencyProvider provider : list.providers) {
					DependencyProvider.DependencyInfo info = DependencyInfo.create(project, dependency, configuration);

					try {
						provider.provide(info, afterTasks::add);
					} catch (Exception e) {
						throw new RuntimeException("Failed to provide " + dependency.getGroup() + ":" + dependency.getName() + ":" + dependency.getVersion() + " : " + e.toString(), e);
					}
				}
			}
		}

		SourceRemapper sourceRemapper = new SourceRemapper(project, true);
		String mappingsIdentifier = mappingsProvider.mappingsIdentifier();

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

		if (extension.getInstallerData() == null) {
			project.getLogger().warn("fabric-installer.json not found in classpath!");
		}

		ModCompileRemapper.remapDependencies(project, mappingsIdentifier, extension, sourceRemapper);

		sourceRemapper.remapAll();

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
			if (!IntelijUtils.isIdeaSync() && extension.getMixin().getUseLegacyMixinAp().get()) {
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
