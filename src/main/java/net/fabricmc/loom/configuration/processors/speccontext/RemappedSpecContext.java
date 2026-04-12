/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022-2023 FabricMC
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

package net.fabricmc.loom.configuration.processors.speccontext;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.jspecify.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.api.RemapConfigurationSettings;
import net.fabricmc.loom.api.processor.SpecContext;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftSourceSets;
import net.fabricmc.loom.util.AsyncCache;
import net.fabricmc.loom.util.fmj.FabricModJson;
import net.fabricmc.loom.util.fmj.FabricModJsonFactory;
import net.fabricmc.loom.util.fmj.FabricModJsonHelpers;

/**
 * @param modDependencies External mods that are depended on
 * @param localMods Mods found in the current project.
 * @param compileRuntimeMods Dependent mods found in both the compile and runtime classpath.
 */
public record RemappedSpecContext(
		List<FabricModJson> modDependencies,
		List<FabricModJson> localMods,
		List<ModHolder> compileRuntimeMods,
		MappingsNamespace productionNamespace) implements SpecContext {
	public static RemappedSpecContext create(Project project) {
		return create(new RemappedProjectView.Impl(project));
	}

	@VisibleForTesting
	public static RemappedSpecContext create(RemappedProjectView projectView) {
		AsyncCache<List<FabricModJson>> fmjCache = new AsyncCache<>();
		return new RemappedSpecContext(
				getDependentMods(projectView, fmjCache),
				projectView.getMods(),
				getCompileRuntimeMods(projectView, fmjCache),
				projectView.getProductionNamespace()
		);
	}

	// Returns a list of mods found on both the compile and/or runtime classpaths
	private static List<FabricModJson> getDependentMods(RemappedProjectView projectView, AsyncCache<List<FabricModJson>> fmjCache) {
		var futures = new ArrayList<CompletableFuture<List<FabricModJson>>>();

		for (RemapConfigurationSettings entry : projectView.getRemapConfigurations()) {
			final Set<File> artifacts = entry.getSourceConfiguration().get().resolve();

			for (File artifact : artifacts) {
				futures.add(fmjCache.get(artifact.toPath().toAbsolutePath().toString(), () -> {
					return FabricModJsonFactory.createFromZipOptional(artifact.toPath())
							.map(List::of)
							.orElseGet(List::of);
				}));
			}
		}

		if (!projectView.disableProjectDependantMods()) {
			// Add all the dependent projects
			for (Project dependentProject : SpecContext.getDependentProjects(projectView).toList()) {
				futures.add(fmjCache.get(dependentProject.getPath(), () -> FabricModJsonHelpers.getModsInProject(dependentProject)));
			}
		}

		return SpecContext.distinctSorted(AsyncCache.joinList(futures));
	}

	// Returns a list of mods that are on both to compile and runtime classpath
	private static List<ModHolder> getCompileRuntimeMods(RemappedProjectView projectView, AsyncCache<List<FabricModJson>> fmjCache) {
		var mods = new ArrayList<>(getCompileRuntimeModsFromRemapConfigs(projectView, fmjCache));

		for (Project dependentProject : getCompileRuntimeProjectDependencies(projectView).toList()) {
			List<FabricModJson> projectMods = fmjCache.getBlocking(dependentProject.getPath(), () -> {
				return FabricModJsonHelpers.getModsInProject(dependentProject);
			});

			for (FabricModJson mod : projectMods) {
				mods.add(new ModHolder(mod));
			}
		}

		return Collections.unmodifiableList(mods);
	}

	// Returns a list of jar mods that are found on the compile and runtime remapping configurations
	private static List<ModHolder> getCompileRuntimeModsFromRemapConfigs(RemappedProjectView projectView, AsyncCache<List<FabricModJson>> fmjCache) {
		// A set of mod ids from all remap configurations that are considered for dependency transforms.
		final Set<String> runtimeModIds = getModIds(
				ProjectView.ArtifactUsage.RUNTIME,
				projectView,
				fmjCache,
				projectView.getRuntimeRemapConfigurations().stream()
						.filter(settings -> settings.getApplyDependencyTransforms().get())
		);

		// A set of mod ids that are found on one or more remap configurations that target the common source set.
		// Null when split source sets are not enabled, meaning all mods are common.
		final Set<String> commonRuntimeModIds = projectView.areEnvironmentSourceSetsSplit() ? getModIds(
				ProjectView.ArtifactUsage.RUNTIME,
				projectView,
				fmjCache,
				projectView.getRuntimeRemapConfigurations().stream()
						.filter(settings -> settings.getSourceSet().map(sourceSet -> !sourceSet.getName().equals(MinecraftSourceSets.Split.CLIENT_ONLY_SOURCE_SET_NAME)).get())
						.filter(settings -> settings.getApplyDependencyTransforms().get()))
				: null;

		Stream<FabricModJson> compileMods = getMods(
				ProjectView.ArtifactUsage.COMPILE,
				projectView,
				fmjCache,
				projectView.getCompileRemapConfigurations().stream()
						.filter(settings -> settings.getApplyDependencyTransforms().get()));

		return compileMods
				// Only check based on the modid, as there may be differing versions used between the compile and runtime classpath.
				// We assume that the version used at runtime will be binary compatible with the version used to compile against.
				// It's not perfect but better than silently not supplying the mod, and this could happen with regular API that you compile against anyway.
				.filter(fabricModJson -> runtimeModIds.contains(fabricModJson.getId()))
				.sorted(Comparator.comparing(FabricModJson::getId))
				.map(fabricModJson -> new ModHolder(fabricModJson, commonRuntimeModIds == null || commonRuntimeModIds.contains(fabricModJson.getId())))
				.toList();
	}

	private static Stream<FabricModJson> getMods(ProjectView.ArtifactUsage artifactUsage, RemappedProjectView projectView, AsyncCache<List<FabricModJson>> fmjCache, Stream<RemapConfigurationSettings> stream) {
		return stream.flatMap(projectView.resolveArtifacts(artifactUsage))
				.map(modFromZip(fmjCache))
				.filter(Objects::nonNull);
	}

	private static Set<String> getModIds(ProjectView.ArtifactUsage artifactUsage, RemappedProjectView projectView, AsyncCache<List<FabricModJson>> fmjCache, Stream<RemapConfigurationSettings> stream) {
		return getMods(artifactUsage, projectView, fmjCache, stream)
				.map(FabricModJson::getId)
				.collect(Collectors.toSet());
	}

	private static Function<Path, @Nullable FabricModJson> modFromZip(AsyncCache<List<FabricModJson>> fmjCache) {
		return zipPath -> {
			final List<FabricModJson> list = fmjCache.getBlocking(zipPath.toAbsolutePath().toString(), () -> {
				return FabricModJsonFactory.createFromZipOptional(zipPath)
						.map(List::of)
						.orElseGet(List::of);
			});
			return list.isEmpty() ? null : list.getFirst();
		};
	}

	// Returns a list of Loom Projects found in both the runtime and compile classpath
	private static Stream<Project> getCompileRuntimeProjectDependencies(ProjectView projectView) {
		if (projectView.disableProjectDependantMods()) {
			return Stream.empty();
		}

		final Stream<Project> runtimeProjects = projectView.getLoomProjectDependencies(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME);
		final List<Project> compileProjects = projectView.getLoomProjectDependencies(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME).toList();

		return runtimeProjects
				.filter(compileProjects::contains); // Use the intersection of the two configurations.
	}

	@Override
	public List<FabricModJson> modDependenciesCompileRuntime() {
		return compileRuntimeMods.stream()
				.map(ModHolder::mod)
				.toList();
	}

	@Override
	public List<FabricModJson> modDependenciesCompileRuntimeClient() {
		return compileRuntimeMods.stream()
				.filter(modHolder -> !modHolder.common())
				.map(ModHolder::mod)
				.toList();
	}

	private record ModHolder(FabricModJson mod, boolean common) {
		ModHolder(FabricModJson mod) {
			this(mod, true);
		}
	}
}
