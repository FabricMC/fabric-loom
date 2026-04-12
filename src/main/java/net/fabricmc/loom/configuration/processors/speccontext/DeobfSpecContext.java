/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 FabricMC
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;

import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.api.processor.SpecContext;
import net.fabricmc.loom.util.AsyncCache;
import net.fabricmc.loom.util.fmj.FabricModJson;
import net.fabricmc.loom.util.fmj.FabricModJsonFactory;
import net.fabricmc.loom.util.fmj.FabricModJsonHelpers;

public record DeobfSpecContext(List<FabricModJson> modDependencies,
								List<FabricModJson> localMods,
								// Mods that are in the following configurations: [runtimeClasspath, compileClasspath] or [runtimeClientClasspath, compileClientClasspath]
								// These are mods that will be used to transform both the client and server jars
								List<FabricModJson> modDependenciesCompileRuntime,
								// Here we want mods that are ONLY in [runtimeClientClasspath, compileClientClasspath] and not [runtimeClasspath, compileClasspath]
								// These mods will be excluded from transforming the server jar
								List<FabricModJson> modDependenciesCompileRuntimeClient
) implements SpecContext {
	public static DeobfSpecContext create(Project project) {
		return create(new DeobfProjectView.Impl(project));
	}

	public static DeobfSpecContext create(DeobfProjectView projectView) {
		AsyncCache<List<FabricModJson>> fmjCache = new AsyncCache<>();

		FileCollection mainRuntimeClasspath = projectView.getDependencies(DebofConfiguration.RUNTIME, DebofConfiguration.TargetSourceSet.MAIN);
		FileCollection mainCompileClasspath = projectView.getDependencies(DebofConfiguration.COMPILE, DebofConfiguration.TargetSourceSet.MAIN);

		// All mods in both [runtimeClasspath, compileClasspath]
		List<FabricModJson> mainRuntimeMods = getModsFromConfiguration(mainRuntimeClasspath, fmjCache);
		List<FabricModJson> mainCompileMods = getModsFromConfiguration(mainCompileClasspath, fmjCache);

		Set<String> mainRuntimeModIds = toModIdSet(mainRuntimeMods);
		Set<String> mainCompileModIds = toModIdSet(mainCompileMods);
		Set<String> mainTransformingModIds = common(mainRuntimeModIds, mainCompileModIds);

		// All mods in both [runtimeClientClasspath, compileClientClasspath]
		List<FabricModJson> clientRuntimeMods;
		List<FabricModJson> clientCompileMods;
		Set<String> clientTransformingModIds;

		if (projectView.areEnvironmentSourceSetsSplit()) {
			FileCollection clientRuntimeClasspath = projectView.getDependencies(DebofConfiguration.RUNTIME, DebofConfiguration.TargetSourceSet.CLIENT);
			FileCollection clientCompileClasspath = projectView.getDependencies(DebofConfiguration.COMPILE, DebofConfiguration.TargetSourceSet.CLIENT);

			clientRuntimeMods = getModsFromConfiguration(clientRuntimeClasspath, fmjCache);
			clientCompileMods = getModsFromConfiguration(clientCompileClasspath, fmjCache);

			Set<String> clientRuntimeModIds = toModIdSet(clientRuntimeMods);
			Set<String> clientCompileModIds = toModIdSet(clientCompileMods);
			clientTransformingModIds = common(clientRuntimeModIds, clientCompileModIds);
		} else {
			clientRuntimeMods = List.of();
			clientCompileMods = List.of();
			clientTransformingModIds = Set.of();
		}

		// Build the full list of dependent mods from all configurations
		List<FabricModJson> allMods = new ArrayList<>();
		allMods.addAll(mainRuntimeMods);
		allMods.addAll(mainCompileMods);
		allMods.addAll(clientRuntimeMods);
		allMods.addAll(clientCompileMods);

		// Add project dependencies
		if (!projectView.disableProjectDependantMods()) {
			for (Project dependentProject : SpecContext.getDependentProjects(projectView).toList()) {
				allMods.addAll(fmjCache.getBlocking(dependentProject.getPath(), () -> FabricModJsonHelpers.getModsInProject(dependentProject)));
			}
		}

		List<FabricModJson> dependentMods = SpecContext.distinctSorted(allMods);
		Map<String, FabricModJson> mods = dependentMods.stream()
				.collect(HashMap::new, (map, mod) -> map.put(mod.getId(), mod), Map::putAll);

		// All dependency mods that are on both the compile and runtime classpath
		List<FabricModJson> modDependenciesCompileRuntime = new ArrayList<>(getMods(mods, combine(mainTransformingModIds, clientTransformingModIds)));

		// Add all of the project depedencies that are on both the compile and runtime classpath
		modDependenciesCompileRuntime.addAll(getCompileRuntimeProjectMods(projectView, fmjCache));

		return new DeobfSpecContext(
				dependentMods,
				projectView.getMods(),
				modDependenciesCompileRuntime,
				getMods(mods, onlyInLeft(clientTransformingModIds, mainTransformingModIds))
		);
	}

	// Returns a list of all the mods that the current project depends on
	private static List<FabricModJson> getDependentMods(DeobfProjectView projectView, AsyncCache<List<FabricModJson>> fmjCache) {
		var futures = new ArrayList<CompletableFuture<List<FabricModJson>>>();

		for (File artifact : projectView.getFullClasspath().getFiles()) {
			futures.add(fmjCache.get(artifact.toPath().toAbsolutePath().toString(), () -> {
				return getMod(artifact.toPath())
						.map(List::of)
						.orElseGet(List::of);
			}));
		}

		if (!projectView.disableProjectDependantMods()) {
			// Add all the dependent projects
			for (Project dependentProject : SpecContext.getDependentProjects(projectView).toList()) {
				futures.add(fmjCache.get(dependentProject.getPath(), () -> FabricModJsonHelpers.getModsInProject(dependentProject)));
			}
		}

		return SpecContext.distinctSorted(AsyncCache.joinList(futures));
	}

	// Returns a list of mods from a given configuration
	private static List<FabricModJson> getModsFromConfiguration(FileCollection configuration, AsyncCache<List<FabricModJson>> fmjCache) {
		var futures = new ArrayList<CompletableFuture<List<FabricModJson>>>();

		for (File artifact : configuration.getFiles()) {
			futures.add(fmjCache.get(artifact.toPath().toAbsolutePath().toString(), () -> getMod(artifact.toPath())
					.map(List::of)
					.orElseGet(List::of)));
		}

		return SpecContext.distinctSorted(AsyncCache.joinList(futures));
	}

	private static Set<String> toModIdSet(List<FabricModJson> mods) {
		return mods.stream()
				.map(FabricModJson::getId)
				.collect(HashSet::new, Set::add, Set::addAll);
	}

	private static Optional<FabricModJson> getMod(Path path) {
		if (Files.isRegularFile(path)) {
			return FabricModJsonFactory.createFromZipOptional(path);
		}

		return Optional.empty();
	}

	private static List<FabricModJson> getMods(Map<String, FabricModJson> mods, Set<String> ids) {
		List<FabricModJson> result = new ArrayList<>();

		for (String id : ids) {
			result.add(Objects.requireNonNull(mods.get(id), "Could not find mod with id: " + id));
		}

		return result;
	}

	// Returns a list of mods that are on both to compile and runtime classpath
	private static List<FabricModJson> getCompileRuntimeProjectMods(DeobfProjectView projectView, AsyncCache<List<FabricModJson>> fmjCache) {
		var mods = new ArrayList<FabricModJson>();

		for (Project dependentProject : getCompileRuntimeProjectDependencies(projectView).toList()) {
			List<FabricModJson> projectMods = fmjCache.getBlocking(dependentProject.getPath(), () -> FabricModJsonHelpers.getModsInProject(dependentProject));

			mods.addAll(projectMods);
		}

		return Collections.unmodifiableList(mods);
	}

	// Returns a list of Loom Projects found in both the runtime and compile classpath
	private static Stream<Project> getCompileRuntimeProjectDependencies(DeobfProjectView projectView) {
		if (projectView.disableProjectDependantMods()) {
			return Stream.empty();
		}

		final Stream<Project> runtimeProjects = projectView.getProjectDependencies(DebofConfiguration.RUNTIME);
		final List<Project> compileProjects = projectView.getProjectDependencies(DebofConfiguration.COMPILE).toList();

		return runtimeProjects
				.filter(compileProjects::contains); // Use the intersection of the two configurations.
	}

	private static Set<String> common(Set<String> a, Set<String> b) {
		Set<String> copy = new HashSet<>(a);
		copy.retainAll(b);
		return copy;
	}

	private static Set<String> combine(Set<String> a, Set<String> b) {
		Set<String> copy = new HashSet<>(a);
		copy.addAll(b);
		return copy;
	}

	private static Set<String> onlyInLeft(Set<String> left, Set<String> right) {
		Set<String> copy = new HashSet<>(left);
		copy.removeAll(right);
		return copy;
	}

	@Override
	public MappingsNamespace productionNamespace() {
		return MappingsNamespace.OFFICIAL;
	}
}
