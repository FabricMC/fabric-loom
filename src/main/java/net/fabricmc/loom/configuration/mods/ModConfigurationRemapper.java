/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2019-2022 FabricMC
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

package net.fabricmc.loom.configuration.mods;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.FileCollectionDependency;
import org.gradle.api.artifacts.MutableVersionConstraint;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.query.ArtifactResolutionQuery;
import org.gradle.api.artifacts.result.ArtifactResult;
import org.gradle.api.artifacts.result.ComponentArtifactsResult;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.jvm.JvmLibrary;
import org.gradle.language.base.artifact.SourcesArtifact;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.RemapConfigurationSettings;
import net.fabricmc.loom.configuration.mods.dependency.ModDependency;
import net.fabricmc.loom.configuration.mods.dependency.ModDependencyFactory;
import net.fabricmc.loom.util.Checksum;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.ModUtils;
import net.fabricmc.loom.util.OperatingSystem;
import net.fabricmc.loom.util.SourceRemapper;

@SuppressWarnings("UnstableApiUsage")
public class ModConfigurationRemapper {
	// This is a placeholder that is used when the actual group is missing (null or empty).
	// This can happen when the dependency is a FileCollectionDependency or from a flatDir repository.
	public static final String MISSING_GROUP = "unspecified";

	public static void supplyModConfigurations(Project project, String mappingsSuffix, LoomGradleExtension extension, SourceRemapper sourceRemapper) {
		final DependencyHandler dependencies = project.getDependencies();

		for (RemapConfigurationSettings entry : extension.getRemapConfigurations()) {
			entry.getRemappedConfiguration().configure(remappedConfig -> {
				/*
				sourceConfig - The source configuration where the intermediary named artifacts come from. i.e "modApi"
				remappedConfig - an intermediate configuration where the remapped artifacts go
				targetConfig - extends from the remappedConfig, such as "api"
				 */
				final Configuration sourceConfig = entry.getSourceConfiguration().get();
				final Configuration targetConfig = entry.getTargetConfiguration().get();
				final boolean hasClientTarget = entry.getClientSourceConfigurationName().isPresent();

				Configuration clientRemappedConfig = null;

				if (hasClientTarget) {
					clientRemappedConfig = entry.getClientRemappedConfiguration().get();
				}

				final List<ModDependency> modDependencies = new ArrayList<>();

				for (ArtifactRef artifact : resolveArtifacts(project, sourceConfig)) {
					if (!ModUtils.isMod(artifact.path())) {
						artifact.applyToConfiguration(project, targetConfig);
						continue;
					}

					final ModDependency modDependency = ModDependencyFactory.create(artifact, remappedConfig, clientRemappedConfig, mappingsSuffix, project);
					scheduleSourcesRemapping(project, sourceRemapper, modDependency);
					modDependencies.add(modDependency);
				}

				if (modDependencies.isEmpty()) {
					// Nothing else to do
					return;
				}

				final boolean refreshDeps = LoomGradleExtension.get(project).refreshDeps();
				final List<ModDependency> toRemap = modDependencies.stream()
						.filter(dependency -> refreshDeps || dependency.isCacheInvalid(project, null))
						.toList();

				if (!toRemap.isEmpty()) {
					try {
						new ModProcessor(project, sourceConfig).processMods(toRemap);
					} catch (IOException e) {
						throw new UncheckedIOException("Failed to remap mods", e);
					}
				}

				// Add all of the remapped mods onto the config
				for (ModDependency info : modDependencies) {
					info.applyToProject(project);
					createConstraints(info.getInputArtifact(), targetConfig, sourceConfig, dependencies);

					if (clientRemappedConfig != null) {
						createConstraints(info.getInputArtifact(), entry.getClientTargetConfiguration().get(), sourceConfig, dependencies);
					}
				}

				// Export to other projects
				if (entry.getTargetConfigurationName().get().equals(JavaPlugin.API_CONFIGURATION_NAME)) {
					project.getConfigurations().getByName(Constants.Configurations.NAMED_ELEMENTS).extendsFrom(remappedConfig);
				}
			});
		}
	}

	private static void createConstraints(ArtifactRef artifact, Configuration targetConfig, Configuration sourceConfig, DependencyHandler dependencies) {
		if (true) {
			// Disabled due to the gradle module metadata causing issues. Try the MavenProject test to reproduce issue.
			return;
		}

		if (artifact instanceof ArtifactRef.ResolvedArtifactRef mavenArtifact) {
			final String dependencyCoordinate = "%s:%s".formatted(mavenArtifact.group(), mavenArtifact.name());

			// Prevent adding the same un-remapped dependency to the target configuration.
			targetConfig.getDependencyConstraints().add(dependencies.getConstraints().create(dependencyCoordinate, constraint -> {
				constraint.because("configuration (%s) already contains the remapped module from configuration (%s)".formatted(
						targetConfig.getName(),
						sourceConfig.getName()
				));

				constraint.version(MutableVersionConstraint::rejectAll);
			}));
		}
	}

	private static List<ArtifactRef> resolveArtifacts(Project project, Configuration configuration) {
		final List<ArtifactRef> artifacts = new ArrayList<>();

		for (ResolvedArtifact artifact : configuration.getResolvedConfiguration().getResolvedArtifacts()) {
			final Path sources = findSources(project, artifact);
			artifacts.add(new ArtifactRef.ResolvedArtifactRef(artifact, sources));
		}

		// FileCollectionDependency (files/fileTree) doesn't resolve properly,
		// so we have to "resolve" it on our own. The naming is "abc.jar" => "unspecified:abc:unspecified".
		for (FileCollectionDependency dependency : configuration.getAllDependencies().withType(FileCollectionDependency.class)) {
			final String group = replaceIfNullOrEmpty(dependency.getGroup(), () -> MISSING_GROUP);
			final FileCollection files = dependency.getFiles();

			for (File artifact : files) {
				final String name = getNameWithoutExtension(artifact.toPath());
				final String version = replaceIfNullOrEmpty(dependency.getVersion(), () -> Checksum.truncatedSha256(artifact));
				artifacts.add(new ArtifactRef.FileArtifactRef(artifact.toPath(), group, name, version));
			}
		}

		return artifacts;
	}

	private static String getNameWithoutExtension(Path file) {
		final String fileName = file.getFileName().toString();
		final int dotIndex = fileName.lastIndexOf('.');
		return (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);
	}

	@Nullable
	public static Path findSources(Project project, ResolvedArtifact artifact) {
		final DependencyHandler dependencies = project.getDependencies();

		@SuppressWarnings("unchecked") ArtifactResolutionQuery query = dependencies.createArtifactResolutionQuery()
				.forComponents(artifact.getId().getComponentIdentifier())
				.withArtifacts(JvmLibrary.class, SourcesArtifact.class);

		for (ComponentArtifactsResult result : query.execute().getResolvedComponents()) {
			for (ArtifactResult srcArtifact : result.getArtifacts(SourcesArtifact.class)) {
				if (srcArtifact instanceof ResolvedArtifactResult) {
					return ((ResolvedArtifactResult) srcArtifact).getFile().toPath();
				}
			}
		}

		return null;
	}

	private static void scheduleSourcesRemapping(Project project, SourceRemapper sourceRemapper, ModDependency dependency) {
		if (OperatingSystem.isCIBuild()) {
			return;
		}

		final Path sourcesInput = dependency.getInputArtifact().sources();

		if (sourcesInput == null || Files.notExists(sourcesInput)) {
			return;
		}

		if (dependency.isCacheInvalid(project, "sources")) {
			final Path output = dependency.getWorkingFile("sources");

			sourceRemapper.scheduleRemapSources(sourcesInput.toFile(), output.toFile(), false, true, () -> {
				try {
					dependency.copyToCache(project, output, "sources");
				} catch (IOException e) {
					throw new UncheckedIOException("Failed to apply sources to local cache for: " + dependency, e);
				}
			});
		}
	}

	public static String replaceIfNullOrEmpty(@Nullable String s, Supplier<String> fallback) {
		return s == null || s.isEmpty() ? fallback.get() : s;
	}
}
