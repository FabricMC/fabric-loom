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

package net.fabricmc.loom.configuration.processors;

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
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.attributes.Usage;
import org.gradle.api.plugins.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.RemapConfigurationSettings;
import net.fabricmc.loom.api.processor.SpecContext;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftSourceSets;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.fmj.FabricModJson;
import net.fabricmc.loom.util.fmj.FabricModJsonFactory;
import net.fabricmc.loom.util.fmj.FabricModJsonHelpers;
import net.fabricmc.loom.util.AsyncCache;
import net.fabricmc.loom.util.gradle.GradleUtils;

/**
 * @param modDependencies External mods that are depended on
 * @param localMods Mods found in the current project.
 * @param compileRuntimeMods Dependent mods found in both the compile and runtime classpath.
 */
public record SpecContextImpl(
		List<FabricModJson> modDependencies,
		List<FabricModJson> localMods,
		List<ModHolder> compileRuntimeMods) implements SpecContext {
	public static SpecContextImpl create(Project project) {
		AsyncCache<List<FabricModJson>> fmjCache = new AsyncCache<List<FabricModJson>>();
		return new SpecContextImpl(
				getDependentMods(project, fmjCache),
				FabricModJsonHelpers.getModsInProject(project),
				getCompileRuntimeMods(project, fmjCache)
		);
	}

	// Reruns a list of mods found on both the compile and/or runtime classpaths
	private static List<FabricModJson> getDependentMods(Project project, AsyncCache<List<FabricModJson>> fmjCache) {
		final LoomGradleExtension extension = LoomGradleExtension.get(project);
		var futures = new ArrayList<CompletableFuture<List<FabricModJson>>>();

		for (RemapConfigurationSettings entry : extension.getRemapConfigurations()) {
			final Set<File> artifacts = entry.getSourceConfiguration().get().resolve();

			for (File artifact : artifacts) {
				futures.add(fmjCache.get(artifact.toPath().toAbsolutePath().toString(), () -> {
					return FabricModJsonFactory.createFromZipOptional(artifact.toPath())
							.map(List::of)
							.orElseGet(List::of);
				}));
			}
		}

		// TODO provide a project isolated way of doing this.
		if (!extension.isProjectIsolationActive() && !GradleUtils.getBooleanProperty(project, Constants.Properties.DISABLE_PROJECT_DEPENDENT_MODS)) {
			// Add all the dependent projects
			for (Project dependentProject : getDependentProjects(project).toList()) {
				futures.add(fmjCache.get(dependentProject.getPath(), () -> FabricModJsonHelpers.getModsInProject(dependentProject)));
			}
		}

		return sorted(AsyncCache.joinList(futures));
	}

	private static Stream<Project> getDependentProjects(Project project) {
		final Stream<Project> runtimeProjects = getLoomProjectDependencies(project, project.getConfigurations().getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME));
		final Stream<Project> compileProjects = getLoomProjectDependencies(project, project.getConfigurations().getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME));

		return Stream.concat(runtimeProjects, compileProjects)
				.distinct();
	}

	// Returns a list of mods that are on both to compile and runtime classpath
	private static List<ModHolder> getCompileRuntimeMods(Project project, AsyncCache<List<FabricModJson>> fmjCache) {
		var mods = new ArrayList<>(getCompileRuntimeModsFromRemapConfigs(project, fmjCache));

		for (Project dependentProject : getCompileRuntimeProjectDependencies(project).toList()) {
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
	private static List<ModHolder> getCompileRuntimeModsFromRemapConfigs(Project project, AsyncCache<List<FabricModJson>> fmjCache) {
		final LoomGradleExtension extension = LoomGradleExtension.get(project);

		// A set of mod ids from all remap configurations that are considered for dependency transforms.
		final Set<String> runtimeModIds = getModIds(
				project,
				fmjCache,
				extension.getRuntimeRemapConfigurations().stream()
						.filter(settings -> settings.getApplyDependencyTransforms().get())
		);

		// A set of mod ids that are found on one or more remap configurations that target the common source set.
		// Null when split source sets are not enabled, meaning all mods are common.
		final Set<String> commonModIds = extension.areEnvironmentSourceSetsSplit() ? getModIds(
				project,
				fmjCache,
				extension.getRuntimeRemapConfigurations().stream()
						.filter(settings -> settings.getSourceSet().map(sourceSet -> !sourceSet.getName().equals(MinecraftSourceSets.Split.CLIENT_ONLY_SOURCE_SET_NAME)).get())
						.filter(settings -> settings.getApplyDependencyTransforms().get()))
				: null;

		return getMods(
				project,
				fmjCache,
				extension.getCompileRemapConfigurations().stream()
					.filter(settings -> settings.getApplyDependencyTransforms().get()))
				// Only check based on the modid, as there may be differing versions used between the compile and runtime classpath.
				// We assume that the version used at runtime will be binary compatible with the version used to compile against.
				// It's not perfect but better than silently not supplying the mod, and this could happen with regular API that you compile against anyway.
				.filter(fabricModJson -> runtimeModIds.contains(fabricModJson.getId()))
				.sorted(Comparator.comparing(FabricModJson::getId))
				.map(fabricModJson -> new ModHolder(fabricModJson, commonModIds == null || commonModIds.contains(fabricModJson.getId())))
				.toList();
	}

	private static Stream<FabricModJson> getMods(Project project, AsyncCache<List<FabricModJson>> fmjCache, Stream<RemapConfigurationSettings> stream) {
		return stream.flatMap(resolveArtifacts(project, true))
				.map(modFromZip(fmjCache))
				.filter(Objects::nonNull);
	}

	private static Set<String> getModIds(Project project, AsyncCache<List<FabricModJson>> fmjCache, Stream<RemapConfigurationSettings> stream) {
		return getMods(project, fmjCache, stream)
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

	private static Function<RemapConfigurationSettings, Stream<Path>> resolveArtifacts(Project project, boolean runtime) {
		final Usage usage = project.getObjects().named(Usage.class, runtime ? Usage.JAVA_RUNTIME : Usage.JAVA_API);

		return settings -> {
			final Configuration configuration = settings.getSourceConfiguration().get().copyRecursive();
			configuration.setCanBeConsumed(false);
			configuration.attributes(attributes -> attributes.attribute(Usage.USAGE_ATTRIBUTE, usage));
			return configuration.resolve().stream().map(File::toPath);
		};
	}

	// Returns a list of Loom Projects found in both the runtime and compile classpath
	private static Stream<Project> getCompileRuntimeProjectDependencies(Project project) {
		final Stream<Project> runtimeProjects = getLoomProjectDependencies(project, project.getConfigurations().getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME));
		final List<Project> compileProjects = getLoomProjectDependencies(project, project.getConfigurations().getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME)).toList();

		return runtimeProjects
				.filter(compileProjects::contains); // Use the intersection of the two configurations.
	}

	// Returns a list of Loom Projects found in the provided Configuration
	private static Stream<Project> getLoomProjectDependencies(Project project, Configuration configuration) {
		return configuration.getAllDependencies()
				.withType(ProjectDependency.class)
				.stream()
				.map((d) -> project.project(d.getPath()))
				.filter(GradleUtils::isLoomProject);
	}

	// Sort to ensure stable caching
	private static List<FabricModJson> sorted(List<FabricModJson> mods) {
		return mods.stream().sorted(Comparator.comparing(FabricModJson::getId)).toList();
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
