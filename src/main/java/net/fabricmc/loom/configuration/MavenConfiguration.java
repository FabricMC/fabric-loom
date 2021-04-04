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

package net.fabricmc.loom.configuration;

import org.gradle.api.Project;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;

import net.fabricmc.loom.LoomGradleExtension;

public class MavenConfiguration {
	public static void setup(Project project) {
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);

		project.getRepositories().flatDir(repo -> {
			repo.setName("UserLocalCacheFiles");
			repo.dir(extension.getRootProjectBuildCache());
		});

		project.getRepositories().maven(repo -> {
			repo.setName("UserLocalRemappedMods");
			repo.setUrl(extension.getRemappedModCache());
		});

		project.getRepositories().maven(repo -> {
			repo.setName("Fabric");
			repo.setUrl("https://maven.fabricmc.net/");
		});

		project.getRepositories().maven(repo -> {
			repo.setName("Mojang");
			repo.setUrl("https://libraries.minecraft.net/");
		});

		project.getRepositories().maven(repo -> {
			repo.setName("Forge");
			repo.setUrl("https://files.minecraftforge.net/maven/");

			repo.metadataSources(sources -> {
				sources.mavenPom();

				try {
					MavenArtifactRepository.MetadataSources.class.getDeclaredMethod("ignoreGradleMetadataRedirection")
							.invoke(sources);
				} catch (Throwable ignored) {
					// Method not available
				}
			});
		});

		project.getRepositories().mavenCentral();
	}
}
