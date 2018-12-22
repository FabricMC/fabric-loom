/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016, 2017, 2018 FabricMC
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

package net.fabricmc.loom.util;

import com.google.gson.JsonObject;
import net.fabricmc.loom.LoomGradleExtension;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class LoomDependencyManager {

	private List<DependencyProvider> dependencyProviderList = new ArrayList<>();

	public void addProvider(DependencyProvider provider){
		if(dependencyProviderList.contains(provider)){
			throw new RuntimeException("Provider is already registered");
		}
		if(getProvider(provider.getClass()) != null){
			throw new RuntimeException("Provider of this type is already registered");
		}
		provider.register(this);
		dependencyProviderList.add(provider);
	}

	public <T> T getProvider(Class<T> clazz){
		for(DependencyProvider provider : dependencyProviderList){
			if(provider.getClass() == clazz){
				return (T) provider;
			}
		}
		return null;
	}

	public void handleDependencies(Project project){
		List<Runnable> afterTasks = new ArrayList<>();

		project.getLogger().lifecycle(":setting up loom dependencies");
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		Set<String> targetConfigs = new LinkedHashSet<>();
		for(DependencyProvider provider : dependencyProviderList){
			targetConfigs.add(provider.getTargetConfig());
		}
		for(String config : targetConfigs){
			Configuration configuration = project.getConfigurations().getByName(config);
			configuration.getDependencies().forEach(dependency -> {
				for(DependencyProvider provider : dependencyProviderList){
					if(provider.getTargetConfig().equals(config)){
						DependencyProvider.DependencyInfo info = new DependencyProvider.DependencyInfo(project, dependency, configuration);
						try {
							provider.provide(info, project, extension, afterTasks::add);
						} catch (Exception e) {
							throw new RuntimeException("Failed to provide", e);
						}
					}
				}
			});
		}

		if (extension.getInstallerJson() != null) {
			handleInstallerJson(extension.getInstallerJson(), project);
		} else {
			project.getLogger().warn("fabric-installer.json not found in classpath!");
		}

		for (Runnable runnable : afterTasks) {
			runnable.run();
		}
	}

	private static void handleInstallerJson(JsonObject jsonObject, Project project){
		JsonObject libraries = jsonObject.get("libraries").getAsJsonObject();
		libraries.get("common").getAsJsonArray().forEach(jsonElement -> {
			String name = jsonElement.getAsJsonObject().get("name").getAsString();

			Configuration configuration = project.getConfigurations().getByName("compile");
			ExternalModuleDependency modDep = (ExternalModuleDependency) project.getDependencies().create(name);
			modDep.setTransitive(false);
			configuration.getDependencies().add(modDep);

			if(jsonElement.getAsJsonObject().has("url")){
				String url = jsonElement.getAsJsonObject().get("url").getAsString();
				long count = project.getRepositories().stream()
						.filter(artifactRepository -> artifactRepository instanceof MavenArtifactRepository)
						.map(artifactRepository -> (MavenArtifactRepository) artifactRepository)
						.filter(mavenArtifactRepository -> mavenArtifactRepository.getUrl().toString().equalsIgnoreCase(url)).count();
				if(count == 0){
					project.getRepositories().maven(mavenArtifactRepository -> mavenArtifactRepository.setUrl(jsonElement.getAsJsonObject().get("url").getAsString()));
				}

			}
		});
	}
}
