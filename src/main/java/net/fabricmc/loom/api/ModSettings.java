/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022 FabricMC
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

package net.fabricmc.loom.api;

import java.io.File;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.gradle.api.Named;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.SourceSet;
import org.jetbrains.annotations.ApiStatus;

import net.fabricmc.loom.LoomCompanionGradlePlugin;
import net.fabricmc.loom.configuration.classpathgroups.ExternalClasspathGroup;
import net.fabricmc.loom.util.gradle.SourceSetHelper;
import net.fabricmc.loom.util.gradle.SourceSetReference;

/**
 * A {@link Named} object for setting mod-related values. The {@linkplain Named#getName() name} should match the mod id.
 */
public abstract class ModSettings implements Named {
	/**
	 * List of classpath directories, or jar files used to populate the `fabric.classPathGroups` Fabric Loader system property.
	 */
	public abstract ConfigurableFileCollection getModFiles();

	@Inject
	public ModSettings() {
		getExternalGroups().finalizeValueOnRead();
		getModFiles().finalizeValueOnRead();
	}

	/**
	 * Add {@link SourceSet}'s output directories from the current project to be grouped with the named mod.
	 */
	public void sourceSet(SourceSet sourceSet) {
		Project project = getProject();

		if (!SourceSetHelper.isSourceSetOfProject(sourceSet, project)) {
			getProject().getLogger().info("Computing owner project for SourceSet {} as it is not a sourceset of {}", sourceSet.getName(), project.getPath());
			project = SourceSetHelper.getSourceSetProject(sourceSet);

			if (project == getProject()) {
				throw new IllegalStateException("isSourceSetOfProject lied, report to loom devs.");
			}
		}

		sourceSet(sourceSet, project);
	}

	/**
	 * Add {@link SourceSet}'s output directories from the current project to be grouped with the named mod.
	 *
	 * @param name the name of the source set
	 */
	public void sourceSet(String name) {
		sourceSet(name, getProject());
	}

	/**
	 * Add {@link SourceSet}'s output directories from the supplied project to be grouped with the named mod.
	 * @deprecated Replaced with {@link #sourceSet(String, String)} to avoid passing a project reference.
	 */
	@Deprecated
	public void sourceSet(SourceSet sourceSet, Project project) {
		ensureCompanion(project);

		sourceSet(sourceSet.getName(), project.getPath());
	}

	/**
	 * Add {@link SourceSet}'s output directories from the supplied project to be grouped with the named mod.
	 *
	 * @param name the name of the source set
	 * @deprecated Replaced with {@link #sourceSet(String, String)} to avoid passing a project reference.
	 */
	@Deprecated
	public void sourceSet(String name, Project project) {
		ensureCompanion(project);

		sourceSet(name, project.getPath());
	}

	/**
	 * Add {@link SourceSet}'s output directories from the supplied project to be grouped with the named mod.
	 *
	 * <p>If the other project is not a Loom project you must apply the `net.fabricmc.fabric-loom-companion` plugin.
	 *
	 * @param sourceSetName the name of the source set
	 * @param projectPath the path of the project the source set belongs to
	 */
	public void sourceSet(String sourceSetName, String projectPath) {
		if (projectPath.equals(getProject().getPath())) {
			// Shortcut for source sets in our own project.
			SourceSetReference ref = new SourceSetReference(SourceSetHelper.getSourceSetByName(sourceSetName, getProject()), getProject());
			List<File> classpath = SourceSetHelper.getClasspath(ref, false);
			getModFiles().from(classpath);
			return;
		}

		getExternalGroups().add(new ExternalClasspathGroup(projectPath, sourceSetName));
	}

	/**
	 * Add a number of {@link Dependency} to the mod's classpath group. Should be used to include all dependencies that are shaded into your mod.
	 *
	 * <p>Uses a detached configuration.
	 */
	public void dependency(Dependency... dependencies) {
		Configuration detachedConfiguration = getProject().getConfigurations().detachedConfiguration(dependencies);
		configuration(detachedConfiguration);
	}

	/**
	 * Add a {@link Configuration} to the mod's classpath group. Should be used to include all dependencies that are shaded into your mod.
	 */
	public void configuration(Configuration configuration) {
		getModFiles().from(configuration);
	}

	/**
	 * List of {@link ExternalClasspathGroup} that will later be resolved to populate the classpath groups from another Gradle project.
	 */
	@ApiStatus.Internal
	public abstract ListProperty<ExternalClasspathGroup> getExternalGroups();

	@Inject
	public abstract Project getProject();

	@Override
	public String toString() {
		return "ModSettings '" + getName() + "'";
	}

	private void ensureCompanion(Project project) {
		if (project == getProject()) {
			return;
		}

		project.apply(Map.of("plugin", LoomCompanionGradlePlugin.NAME));
	}
}
