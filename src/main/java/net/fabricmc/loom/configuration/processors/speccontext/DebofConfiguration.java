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

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.tasks.SourceSet;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.LoomConfigurations;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.Strings;

/**
 * This mess is created out of the need to resolve the compile/runtime dependencies, without using the actual runtime/compile classpath.
 * As we need to add the minecraft jar to it later (based on the compile/runtime dependencies).
 */
public record DebofConfiguration(String name, List<Function<SourceSet, String>> configurationFunctions) {
	public static final DebofConfiguration COMPILE = new DebofConfiguration("compile", List.of(
			SourceSet::getImplementationConfigurationName,
			SourceSet::getApiConfigurationName,
			SourceSet::getCompileOnlyConfigurationName,
			SourceSet::getCompileOnlyApiConfigurationName
	));
	public static final DebofConfiguration RUNTIME = new DebofConfiguration("runtime", List.of(
			SourceSet::getImplementationConfigurationName,
			SourceSet::getApiConfigurationName,
			SourceSet::getRuntimeOnlyConfigurationName,
			s -> Constants.Configurations.LOCAL_RUNTIME
	));
	public static final List<DebofConfiguration> ALL = List.of(COMPILE, RUNTIME);

	public Configuration getConfiguration(Project project, TargetSourceSet targetSourceSet) {
		return project.getConfigurations().getByName(resolveConfigurationName(targetSourceSet.getSourceSet(project)));
	}

	public List<Configuration> getConfigurations(Project project) {
		ConfigurationContainer configurations = project.getConfigurations();
		return TargetSourceSet.applicable(project).stream()
				.map(target -> target.getSourceSet(project))
				.map(this::resolveConfigurationName)
				.map(configurations::getByName)
				.toList();
	}

	private String resolveConfigurationName(SourceSet sourceSet) {
		return "%s%sDependencyResolve".formatted(sourceSet.getName(), Strings.capitalize(name()));
	}

	public static void create(Project project) {
		for (DebofConfiguration debofConfiguration : ALL) {
			debofConfiguration.createResolveConfiguration(project);
		}
	}

	public void createResolveConfiguration(Project project) {
		ConfigurationContainer configurations = project.getConfigurations();

		for (TargetSourceSet target : TargetSourceSet.applicable(project)) {
			SourceSet sourceSet = target.getSourceSet(project);

			configurations.register(resolveConfigurationName(sourceSet), c -> {
				LoomConfigurations.Role.RESOLVABLE.apply(c);

				for (Function<SourceSet, String> configProvider : configurationFunctions()) {
					Configuration sourceConfig = configurations.findByName(configProvider.apply(sourceSet));

					if (sourceConfig == null) {
						continue;
					}

					c.extendsFrom(sourceConfig);
				}
			});
		}
	}

	public enum TargetSourceSet {
		MAIN(LoomGradleExtension::getMinecraftSourceSet, e -> Boolean.TRUE),
		CLIENT(LoomGradleExtension::getMinecraftClientSourceSet, LoomGradleExtension::areEnvironmentSourceSetsSplit);

		private final Function<LoomGradleExtension, SourceSet> sourceSetGetter;
		private final Function<LoomGradleExtension, Boolean> enabled;

		TargetSourceSet(Function<LoomGradleExtension, SourceSet> sourceSetGetter, Function<LoomGradleExtension, Boolean> enabled) {
			this.sourceSetGetter = sourceSetGetter;
			this.enabled = enabled;
		}

		public SourceSet getSourceSet(Project project) {
			return sourceSetGetter.apply(LoomGradleExtension.get(project));
		}

		public static List<TargetSourceSet> applicable(Project project) {
			LoomGradleExtension extension = LoomGradleExtension.get(project);
			return Arrays.stream(values())
					.filter(targetSourceSet -> targetSourceSet.enabled.apply(extension))
					.toList();
		}
	}
}
