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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

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

public class ModCompileRemapper {
	public static void remapDependencies(Project project, String mappingsSuffix, LoomGradleExtension extension, Configuration modCompile, Configuration modCompileRemapped, Configuration regularCompile, Consumer<Runnable> postPopulationScheduler) {
		Logger logger = project.getLogger();
		DependencyHandler dependencies = project.getDependencies();

		for (ResolvedArtifact artifact : modCompile.getResolvedConfiguration().getResolvedArtifacts()) {
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
				addToRegularCompile(project, regularCompile, notation);
				continue;
			}

			File sources = findSources(dependencies, artifact);

			String remappedLog = group + ":" + name + ":" + version + classifierSuffix + " (" + mappingsSuffix + ")";
			String remappedNotation = String.format("%s:%s:%s@%s%s", group, name, version, mappingsSuffix, classifierSuffix);
			String remappedFilename = String.format("%s-%s@%s", name, version, mappingsSuffix + classifierSuffix.replace(':', '-'));
			project.getLogger().info(":providing " + remappedLog);

			File modStore = extension.getRemappedModCache();

			remapArtifact(project, modCompileRemapped, artifact, remappedFilename, modStore);

			project.getDependencies().add(modCompileRemapped.getName(), project.getDependencies().module(remappedNotation));

			if (sources != null) {
				scheduleSourcesRemapping(project, postPopulationScheduler, sources, remappedLog, remappedFilename, modStore);
			}
		}
	}

	/**
	 * Checks if an artifact is a fabric mod, according to the presence of a fabric.mod.json.
	 */
	private static boolean isFabricMod(Project project, Logger logger, ResolvedArtifact artifact, String notation) {
		File input = artifact.getFile();
		AtomicBoolean fabricMod = new AtomicBoolean(false);
		project.zipTree(input).visit(f -> {
			if (f.getName().endsWith("fabric.mod.json")) {
				logger.info("Found Fabric mod in modCompile: {}", notation);
				fabricMod.set(true);
				f.stopVisiting();
			}
		});
		return fabricMod.get();
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

	private static void remapArtifact(Project project, Configuration config, ResolvedArtifact artifact, String remappedFilename, File modStore) {
		File input = artifact.getFile();
		File output = new File(modStore, remappedFilename + ".jar");

		if (!output.exists() || input.lastModified() <= 0 || input.lastModified() > output.lastModified()) {
			//If the output doesn't exist, or appears to be outdated compared to the input we'll remap it
			try {
				ModProcessor.processMod(input, output, project, config, artifact);
			} catch (IOException e) {
				throw new RuntimeException("Failed to remap mod", e);
			}

			if (!output.exists()) {
				throw new RuntimeException("Failed to remap mod");
			}

			output.setLastModified(input.lastModified());
		} else {
			project.getLogger().info(output.getName() + " is up to date with " + input.getName());
		}
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

	private static void scheduleSourcesRemapping(Project project, Consumer<Runnable> postPopulationScheduler, File sources, String remappedLog, String remappedFilename, File modStore) {
		postPopulationScheduler.accept(() -> {
			project.getLogger().info(":providing " + remappedLog + " sources");
			File remappedSources = new File(modStore, remappedFilename + "-sources.jar");

			if (!remappedSources.exists() || sources.lastModified() <= 0 || sources.lastModified() > remappedSources.lastModified()) {
				try {
					SourceRemapper.remapSources(project, sources, remappedSources, true);

					//Set the remapped sources creation date to match the sources if we're likely succeeded in making it
					remappedSources.setLastModified(sources.lastModified());
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else {
				project.getLogger().info(remappedSources.getName() + " is up to date with " + sources.getName());
			}
		});
	}
}
