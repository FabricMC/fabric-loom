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

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.providers.MappingsProvider;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.ModProcessor;
import net.fabricmc.loom.util.SourceRemapper;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.query.ArtifactResolutionQuery;
import org.gradle.api.artifacts.result.ArtifactResult;
import org.gradle.api.artifacts.result.ComponentArtifactsResult;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.logging.Logger;
import org.gradle.jvm.JvmLibrary;
import org.gradle.language.base.artifact.SourcesArtifact;

import java.io.File;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class ModCompileRemapper {
	public static void remapDependencies(Project project, String mappingsPrefix, LoomGradleExtension extension, Configuration modCompile, Configuration modCompileRemapped, Configuration regularCompile, Consumer<Runnable> postPopulationScheduler) {
		Logger logger = project.getLogger();
		DependencyHandler dependencies = project.getDependencies();

		for (ResolvedArtifactResult artifact : modCompile.getIncoming().getArtifacts().getArtifacts()) {
			String group;
			String name;
			String version;

			if (artifact.getId().getComponentIdentifier() instanceof ModuleComponentIdentifier) {
				group = ((ModuleComponentIdentifier) artifact.getId().getComponentIdentifier()).getGroup();
				name = ((ModuleComponentIdentifier) artifact.getId().getComponentIdentifier()).getModule();
				version = ((ModuleComponentIdentifier) artifact.getId().getComponentIdentifier()).getVersion();
			} else {
				group = "net.fabricmc.synthetic";
				name = artifact.getId().getComponentIdentifier().getDisplayName().replace('.', '-');
				version = "0.1.0";
			}

			String notation = group + ":" + name + ":" + version;

			File input = artifact.getFile();
			AtomicBoolean isFabricMod = new AtomicBoolean(false);
			project.zipTree(input).visit(f -> {
				if (f.getName().endsWith("fabric.mod.json")) {
					logger.info("Found Fabric mod in modCompile: {}", notation);
					isFabricMod.set(true);
					f.stopVisiting();
				}
			});

			if (!isFabricMod.get()) {
				project.getLogger().lifecycle(":providing " + notation);
				Dependency dep = dependencies.module(notation);
				if (dep instanceof ModuleDependency) {
					((ModuleDependency) dep).setTransitive(false);
				}
				dependencies.add(regularCompile.getName(), dep);
				continue;
			}

			AtomicReference<File> sources = new AtomicReference<>();
			@SuppressWarnings ("unchecked")
			ArtifactResolutionQuery query = dependencies.createArtifactResolutionQuery()//
					.forComponents(artifact.getId().getComponentIdentifier())//
					.withArtifacts(JvmLibrary.class, SourcesArtifact.class);
			outer:
			for (ComponentArtifactsResult result : query.execute().getResolvedComponents()) {
				for (ArtifactResult srcArtifact : result.getArtifacts(SourcesArtifact.class)) {
					if (srcArtifact instanceof ResolvedArtifactResult) {
						sources.set(((ResolvedArtifactResult) srcArtifact).getFile());
						break outer;
					}
				}
			}

			String remappedLog = group + ":" + name + ":" + version + " (" + mappingsPrefix + ")";
			String remappedNotation = "net.fabricmc.mapped:" + mappingsPrefix + "." + group + "." + name + ":" + version;
			String remappedFilename = mappingsPrefix + "." + group + "." + name + "-" + version;
			project.getLogger().lifecycle(":providing " + remappedLog);

			File modStore = extension.getRemappedModCache();

			File output = new File(modStore, remappedFilename + ".jar");
			if (!output.exists() || input.lastModified() <= 0 || input.lastModified() > output.lastModified()) {
				//If the output doesn't exist, or appears to be outdated compared to the input we'll remap it
				ModProcessor.handleMod(input, output, project);

				if (!output.exists()){
					throw new RuntimeException("Failed to remap mod");
				}

				output.setLastModified(input.lastModified());
			} else {
				project.getLogger().info(output.getName() + " is up to date with " + input.getName());
			}

			project.getDependencies().add(modCompileRemapped.getName(), project.getDependencies().module(remappedNotation));

			if (sources.get() != null) {
				postPopulationScheduler.accept(() -> {
					project.getLogger().lifecycle(":providing " + remappedLog + " sources");
					File remappedSources = new File(modStore, remappedFilename + "-sources.jar");

					if (!remappedSources.exists() || sources.get().lastModified() <= 0 || sources.get().lastModified() > remappedSources.lastModified()) {
						try {
							SourceRemapper.remapSources(project, sources.get(), remappedSources, true);

							//Set the remapped sources creation date to match the sources if we're likely succeeded in making it
							remappedSources.setLastModified(sources.get().lastModified());
						} catch (Exception e) {
							e.printStackTrace();
						}
					} else {
						project.getLogger().info(remappedSources.getName() + " is up to date with " + sources.get().getName());
					}
				});
			}
		}
	}
}
