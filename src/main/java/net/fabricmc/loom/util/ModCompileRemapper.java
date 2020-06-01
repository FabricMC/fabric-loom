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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.query.ArtifactResolutionQuery;
import org.gradle.api.artifacts.result.ArtifactResult;
import org.gradle.api.artifacts.result.ComponentArtifactsResult;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.logging.Logger;
import org.gradle.jvm.JvmLibrary;
import org.gradle.language.base.artifact.SourcesArtifact;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.processors.dependency.ModDependencyInfo;
import net.fabricmc.loom.processors.dependency.RemapData;

public class ModCompileRemapper {
	public static void remapDependencies(Project project, String mappingsSuffix, LoomGradleExtension extension, SourceRemapper sourceRemapper) {
		Logger logger = project.getLogger();
		DependencyHandler dependencies = project.getDependencies();

		final File modStore = extension.getRemappedModCache();
		final RemapData remapData = new RemapData(mappingsSuffix, modStore);

		for (RemappedConfigurationEntry entry : Constants.MOD_COMPILE_ENTRIES) {
			Configuration sourceConfig = project.getConfigurations().getByName(entry.getSourceConfiguration());
			Configuration remappedConfig = project.getConfigurations().getByName(entry.getRemappedConfiguration());
			Configuration regularConfig = project.getConfigurations().getByName(entry.getTargetConfiguration(project.getConfigurations()));

			List<ModDependencyInfo> modDependencies = new ArrayList<>();

			for (ResolvedArtifact artifact : sourceConfig.getResolvedConfiguration().getResolvedArtifacts()) {
				String group;
				String name;
				String version;
				String classifierSuffix = artifact.getClassifier() == null ? "" : (":" + artifact.getClassifier());

				if (artifact.getId().getComponentIdentifier() instanceof ModuleComponentIdentifier) {
					group = ((ModuleComponentIdentifier) artifact.getId().getComponentIdentifier()).getGroup();
					name = ((ModuleComponentIdentifier) artifact.getId().getComponentIdentifier()).getModule();
					version = ((ModuleComponentIdentifier) artifact.getId().getComponentIdentifier()).getVersion();
				} else {
					group = "net.fabricmc.synthetic";
					name = artifact.getId().getComponentIdentifier().getDisplayName().replace('.', '-').replace(" :", "-");
					version = "0.1.0";
				}

				final String notation = group + ":" + name + ":" + version + classifierSuffix;

				if (!isFabricMod(project, logger, artifact, notation)) {
					addToRegularCompile(project, regularConfig, notation);
					continue;
				}

				File sources = findSources(dependencies, artifact);

				ModDependencyInfo info = new ModDependencyInfo(group, name, version, classifierSuffix, artifact.getFile(), sources, remappedConfig, remapData);
				modDependencies.add(info);

				String remappedLog = group + ":" + name + ":" + version + classifierSuffix + " (" + mappingsSuffix + ")";
				project.getLogger().info(":providing " + remappedLog);

				if (sources != null) {
					scheduleSourcesRemapping(project, sourceRemapper, info.sourcesFile, info.getRemappedNotation(), info.getRemappedFilename(), modStore);
				}
			}

			try {
				ModProcessor.processMods(project, modDependencies);
			} catch (IOException e) {
				throw new RuntimeException("Failed to remap mods", e);
			}

			// Add all of the remapped mods onto the config
			for (ModDependencyInfo info : modDependencies) {
				project.getDependencies().add(info.targetConfig.getName(), project.getDependencies().module(info.getRemappedNotation()));
			}
		}
	}

	/**
	 * Checks if an artifact is a fabric mod, according to the presence of a fabric.mod.json.
	 */
	private static boolean isFabricMod(Project project, Logger logger, ResolvedArtifact artifact, String notation) {
		File input = artifact.getFile();

		try (ZipFile zipFile = new ZipFile(input)) {
			if (zipFile.getEntry("fabric.mod.json") != null) {
				logger.info("Found Fabric mod in modCompile: {}", notation);
				return true;
			}

			return false;
		} catch (IOException e) {
			return false;
		}
	}

	private static void addToRegularCompile(Project project, Configuration regularCompile, String notation) {
		project.getLogger().info(":providing " + notation);
		DependencyHandler dependencies = project.getDependencies();
		Dependency dep = dependencies.module(notation);

		if (dep instanceof ModuleDependency) {
			((ModuleDependency) dep).setTransitive(false);
		}

		dependencies.add(regularCompile.getName(), dep);
	}

	public static File findSources(DependencyHandler dependencies, ResolvedArtifact artifact) {
		@SuppressWarnings("unchecked") ArtifactResolutionQuery query = dependencies.createArtifactResolutionQuery()//
				.forComponents(artifact.getId().getComponentIdentifier())//
				.withArtifacts(JvmLibrary.class, SourcesArtifact.class);

		for (ComponentArtifactsResult result : query.execute().getResolvedComponents()) {
			for (ArtifactResult srcArtifact : result.getArtifacts(SourcesArtifact.class)) {
				if (srcArtifact instanceof ResolvedArtifactResult) {
					return ((ResolvedArtifactResult) srcArtifact).getFile();
				}
			}
		}

		return null;
	}

	private static void scheduleSourcesRemapping(Project project, SourceRemapper sourceRemapper, File sources, String remappedLog, String remappedFilename, File modStore) {
		project.getLogger().info(":providing " + remappedLog + " sources");
		File remappedSources = new File(modStore, remappedFilename + "-sources.jar");

		if (!remappedSources.exists() || sources.lastModified() <= 0 || sources.lastModified() > remappedSources.lastModified()) {
			try {
				sourceRemapper.scheduleRemapSources(sources, remappedSources);

				//Set the remapped sources creation date to match the sources if we're likely succeeded in making it
				remappedSources.setLastModified(sources.lastModified());
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else {
			project.getLogger().info(remappedSources.getName() + " is up to date with " + sources.getName());
		}
	}
}
