/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2019-2023 FabricMC
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.google.common.collect.ImmutableMap;
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
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSet;
import org.gradle.jvm.JvmLibrary;
import org.gradle.language.base.artifact.SourcesArtifact;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.RemapConfigurationSettings;
import net.fabricmc.loom.configuration.RemapConfigurations;
import net.fabricmc.loom.configuration.mods.dependency.ModDependency;
import net.fabricmc.loom.configuration.mods.dependency.ModDependencyFactory;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftSourceSets;
import net.fabricmc.loom.util.Checksum;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.SourceRemapper;
import net.fabricmc.loom.util.gradle.SourceSetHelper;
import net.fabricmc.loom.util.service.SharedServiceManager;

@SuppressWarnings("UnstableApiUsage")
public class ModConfigurationRemapper {
	// This is a placeholder that is used when the actual group is missing (null or empty).
	// This can happen when the dependency is a FileCollectionDependency or from a flatDir repository.
	public static final String MISSING_GROUP = "unspecified";

	public static void supplyModConfigurations(Project project, SharedServiceManager serviceManager, String mappingsSuffix, LoomGradleExtension extension, SourceRemapper sourceRemapper) {
		final DependencyHandler dependencies = project.getDependencies();
		// The configurations where the source and remapped artifacts go.
		// key: source, value: target
		final Map<Configuration, Configuration> configsToRemap = new LinkedHashMap<>();
		// Client remapped dep collectors for split source sets. Same keys and values.
		final Map<Configuration, Configuration> clientConfigsToRemap = new HashMap<>();

		for (RemapConfigurationSettings entry : extension.getRemapConfigurations()) {
			// key: true if runtime, false if compile
			final Map<Boolean, Boolean> envToEnabled = ImmutableMap.of(
					false, entry.getOnCompileClasspath().get(),
					true, entry.getOnRuntimeClasspath().get()
			);

			envToEnabled.forEach((runtime, enabled) -> {
				if (!enabled) return;

				final Configuration target = RemapConfigurations.getOrCreateCollectorConfiguration(project, entry, runtime);
				// We copy the source with the desired usage type to get only the runtime or api jars, not both.
				final Configuration sourceCopy = entry.getSourceConfiguration().get().copyRecursive();
				final Usage usage = project.getObjects().named(Usage.class, runtime ? Usage.JAVA_RUNTIME : Usage.JAVA_API);
				sourceCopy.attributes(attributes -> attributes.attribute(Usage.USAGE_ATTRIBUTE, usage));
				configsToRemap.put(sourceCopy, target);

				// If our remap configuration entry targets the client source set as well,
				// let's set up a collector for it too.
				if (entry.getClientSourceConfigurationName().isPresent()) {
					final SourceSet clientSourceSet = SourceSetHelper.getSourceSetByName(MinecraftSourceSets.Split.CLIENT_ONLY_SOURCE_SET_NAME, project);
					final Configuration clientTarget = RemapConfigurations.getOrCreateCollectorConfiguration(project, clientSourceSet, runtime);
					clientConfigsToRemap.put(sourceCopy, clientTarget);
				}
			});

			// Export to other projects.
			if (entry.getTargetConfigurationName().get().equals(JavaPlugin.API_CONFIGURATION_NAME)) {
				// Note: legacy (pre-1.1) behavior is kept for this remapping since
				// we don't have a modApiElements/modRuntimeElements kind of configuration.
				// TODO: Expose API/runtime usage attributes for namedElements to make it work like normal project dependencies.
				final Configuration remappedConfig = project.getConfigurations().maybeCreate(entry.getRemappedConfigurationName());
				remappedConfig.setTransitive(false);
				project.getConfigurations().getByName(Constants.Configurations.NAMED_ELEMENTS).extendsFrom(remappedConfig);
				configsToRemap.put(entry.getSourceConfiguration().get(), remappedConfig);
			}
		}

		// Round 1: Discovery
		// Go through all the configs to find artifacts to remap and
		// the installer data. The installer data has to be added before
		// any mods are remapped since remapping needs the dependencies provided by that data.
		final Map<Configuration, List<ModDependency>> dependenciesBySourceConfig = new HashMap<>();
		configsToRemap.forEach((sourceConfig, remappedConfig) -> {
			/*
			sourceConfig - The source configuration where the intermediary named artifacts come from. i.e "modApi"
			remappedConfig - The target configuration where the remapped artifacts go
			 */
			final Configuration clientRemappedConfig = clientConfigsToRemap.get(sourceConfig);
			final List<ModDependency> modDependencies = new ArrayList<>();

			for (ArtifactRef artifact : resolveArtifacts(project, sourceConfig)) {
				final ArtifactMetadata artifactMetadata;

				try {
					artifactMetadata = ArtifactMetadata.create(artifact);
				} catch (IOException e) {
					throw new UncheckedIOException("Failed to read metadata from" + artifact.path(), e);
				}

				if (artifactMetadata.installerData() != null) {
					if (extension.getInstallerData() != null) {
						project.getLogger().info("Found another installer JSON in ({}), ignoring", artifact.path());
					} else {
						project.getLogger().info("Applying installer data from {}", artifact.path());
						artifactMetadata.installerData().applyToProject(project);
					}
				}

				if (!artifactMetadata.shouldRemap()) {
					// Note: not applying to any type of vanilla Gradle target config like
					// api or implementation to fix https://github.com/FabricMC/fabric-loom/issues/572.
					artifact.applyToConfiguration(project, remappedConfig);
					continue;
				}

				final ModDependency modDependency = ModDependencyFactory.create(artifact, remappedConfig, clientRemappedConfig, mappingsSuffix, project);
				scheduleSourcesRemapping(project, sourceRemapper, modDependency);
				modDependencies.add(modDependency);
			}

			dependenciesBySourceConfig.put(sourceConfig, modDependencies);
		});

		// Round 2: Remapping
		// Remap all discovered artifacts.
		configsToRemap.forEach((sourceConfig, remappedConfig) -> {
			final List<ModDependency> modDependencies = dependenciesBySourceConfig.get(sourceConfig);

			if (modDependencies.isEmpty()) {
				// Nothing else to do
				return;
			}

			final Configuration clientRemappedConfig = clientConfigsToRemap.get(sourceConfig);
			final boolean refreshDeps = LoomGradleExtension.get(project).refreshDeps();
			// TODO: With the same artifacts being considered multiple times for their different
			//   usage attributes, this should probably not process them multiple times even with refreshDeps.
			final List<ModDependency> toRemap = modDependencies.stream()
					.filter(dependency -> refreshDeps || dependency.isCacheInvalid(project, null))
					.toList();

			if (!toRemap.isEmpty()) {
				try {
					new ModProcessor(project, sourceConfig, serviceManager).processMods(toRemap);
				} catch (IOException e) {
					throw new UncheckedIOException("Failed to remap mods", e);
				}
			}

			// Add all of the remapped mods onto the config
			for (ModDependency info : modDependencies) {
				info.applyToProject(project);
				createConstraints(info.getInputArtifact(), remappedConfig, sourceConfig, dependencies);

				if (clientRemappedConfig != null) {
					createConstraints(info.getInputArtifact(), clientRemappedConfig, sourceConfig, dependencies);
				}
			}
		});
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
		if (isCIBuild()) {
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

	private static boolean isCIBuild() {
		final String loomProperty = System.getProperty("fabric.loom.ci");

		if (loomProperty != null) {
			return loomProperty.equalsIgnoreCase("true");
		}

		// CI seems to be set by most popular CI services
		return System.getenv("CI") != null;
	}
}
