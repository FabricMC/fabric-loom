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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;

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
		List<FabricModJson> dependentMods = getDependentMods(projectView, fmjCache);
		Map<String, FabricModJson> mods = dependentMods.stream()
				.collect(HashMap::new, (map, mod) -> map.put(mod.getId(), mod), Map::putAll);

		FileCollection mainRuntimeClasspath = projectView.getDependencies(DebofConfiguration.RUNTIME, DebofConfiguration.TargetSourceSet.MAIN);
		FileCollection mainCompileClasspath = projectView.getDependencies(DebofConfiguration.COMPILE, DebofConfiguration.TargetSourceSet.MAIN);

		// All mods in both [runtimeClasspath, compileClasspath]
		Set<String> mainTransformingModIds = common(
				getModIds(mainRuntimeClasspath, fmjCache),
				getModIds(mainCompileClasspath, fmjCache)
		);

		// All mods in both [runtimeClientClasspath, compileClientClasspath]
		Set<String> clientTransformingModIds;

		if (projectView.areEnvironmentSourceSetsSplit()) {
			FileCollection clientRuntimeClasspath = projectView.getDependencies(DebofConfiguration.RUNTIME, DebofConfiguration.TargetSourceSet.CLIENT);
			FileCollection clientCompileClasspath = projectView.getDependencies(DebofConfiguration.COMPILE, DebofConfiguration.TargetSourceSet.CLIENT);

			clientTransformingModIds = common(
					getModIds(clientRuntimeClasspath, fmjCache),
					getModIds(clientCompileClasspath, fmjCache)
			);
		} else {
			clientTransformingModIds = Set.of();
		}

		return new DeobfSpecContext(
				dependentMods,
				projectView.getMods(),
				getMods(mods, combine(mainTransformingModIds, clientTransformingModIds)),
				getMods(mods, onlyInLeft(clientTransformingModIds, mainTransformingModIds))
		);
	}

	// Returns a list of all the mods that the current project depends on
	private static List<FabricModJson> getDependentMods(DeobfProjectView projectView, AsyncCache<List<FabricModJson>> fmjCache) {
		var futures = new ArrayList<CompletableFuture<List<FabricModJson>>>();
		Set<File> artifacts = projectView.getFullClasspath().getFiles();

		for (File artifact : artifacts) {
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

	// Returns a list of mod ids in a given configuration
	private static Set<String> getModIds(FileCollection configuration, AsyncCache<List<FabricModJson>> fmjCache) {
		var futures = new ArrayList<CompletableFuture<List<FabricModJson>>>();

		Set<File> artifacts = configuration.getFiles();

		for (File artifact : artifacts) {
			futures.add(fmjCache.get(artifact.toPath().toAbsolutePath().toString(), () -> {
				return getMod(artifact.toPath())
						.map(List::of)
						.orElseGet(List::of);
			}));
		}

		return SpecContext.distinctSorted(AsyncCache.joinList(futures)).stream()
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
}
