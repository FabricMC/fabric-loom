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

package net.fabricmc.loom.task.service;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.ModSettings;
import net.fabricmc.loom.configuration.classpathgroups.ClasspathGroup;
import net.fabricmc.loom.configuration.classpathgroups.ExternalClasspathGroup;
import net.fabricmc.loom.configuration.classpathgroups.ExternalClasspathGroupDTO;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.Lazy;
import net.fabricmc.loom.util.service.Service;
import net.fabricmc.loom.util.service.ServiceFactory;
import net.fabricmc.loom.util.service.ServiceType;

public class ClasspathGroupService extends Service<ClasspathGroupService.Options> {
	public static ServiceType<Options, ClasspathGroupService> TYPE = new ServiceType<Options, ClasspathGroupService>(Options.class, ClasspathGroupService.class);

	public interface Options extends Service.Options {
		@Input
		@Optional
		ListProperty<ClasspathGroup> getClasspathGroups();

		@InputFiles
		@Optional
		@PathSensitive(PathSensitivity.NONE)
		ConfigurableFileCollection getExternalClasspathGroups();
	}

	public static Provider<Options> create(Project project) {
		return TYPE.create(project, options -> {
			LoomGradleExtension extension = LoomGradleExtension.get(project);
			NamedDomainObjectContainer<ModSettings> modSettings = extension.getMods();

			if (modSettings.isEmpty()) {
				return;
			}

			options.getClasspathGroups().set(ClasspathGroup.fromModSettings(modSettings));

			if (!hasExternalClasspathGroups(modSettings)) {
				return;
			}

			List<Dependency> externalDependencies = getExternalDependencies(project, modSettings);
			Configuration externalClasspathGroups = project.getConfigurations().detachedConfiguration(externalDependencies.toArray(new Dependency[0]));
			options.getExternalClasspathGroups().from(externalClasspathGroups);
		});
	}

	private static boolean hasExternalClasspathGroups(Set<ModSettings> modSettings) {
		return modSettings.stream()
			.anyMatch(s ->
				s.getExternalGroups().isPresent()
				&& !s.getExternalGroups().get().isEmpty()
		);
	}

	private static List<Dependency> getExternalDependencies(Project project, Set<ModSettings> modSettings) {
		List<String> requiredProjects = modSettings.stream()
				.flatMap(s -> s.getExternalGroups().get().stream())
				.map(ExternalClasspathGroup::projectPath)
				.distinct()
				.toList();

		List<Dependency> dependencies = new ArrayList<>();

		for (String projectPath : requiredProjects) {
			Dependency externalDependency = project.getDependencies()
					.project(Map.of(
							"path", projectPath,
							"configuration", Constants.Configurations.EXPORTED_CLASSPATH
					));
			dependencies.add(externalDependency);
		}

		return Collections.unmodifiableList(dependencies);
	}

	private final Supplier<Map<String, ExternalClasspathGroupDTO>> externalClasspathGroups = Lazy.of(() -> ExternalClasspathGroupDTO.resolveExternal(getOptions().getExternalClasspathGroups().getFiles()));

	public ClasspathGroupService(Options options, ServiceFactory serviceFactory) {
		super(options, serviceFactory);
	}

	public List<File> getClasspath(ClasspathGroup classpathGroup) {
		final List<String> paths = new ArrayList<>();

		for (ExternalClasspathGroup externalGroup : classpathGroup.externalGroups()) {
			ExternalClasspathGroupDTO dto = externalClasspathGroups.get().get(externalGroup.projectPath());

			if (dto == null) {
				throw new IllegalStateException("Could not find resolved external classpath group for project: " + externalGroup.projectPath());
			}

			paths.addAll(dto.getForSourceSet(externalGroup.sourceSetName()));
		}

		paths.addAll(classpathGroup.paths());

		return paths.stream().map(File::new).toList();
	}

	/**
	 * See: https://github.com/FabricMC/fabric-loader/pull/585.
	 */
	public String getClasspathGroupsPropertyValue() {
		return getOptions().getClasspathGroups().get()
				.stream()
				.map(group ->
					getClasspath(group).stream()
						.map(File::getAbsolutePath)
						.collect(Collectors.joining(File.pathSeparator))
				)
				.collect(Collectors.joining(File.pathSeparator+File.pathSeparator));
	}

	public boolean hasGroups() {
		return getOptions().getClasspathGroups().isPresent() && !getOptions().getClasspathGroups().get().isEmpty();
	}
}
