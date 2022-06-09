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

import javax.inject.Inject;

import org.gradle.api.Named;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.SourceSet;
import org.jetbrains.annotations.ApiStatus;

import net.fabricmc.loom.util.gradle.SourceSetHelper;
import net.fabricmc.loom.util.gradle.SourceSetReference;

/**
 * A {@link Named} object for setting mod-related values. The {@linkplain Named#getName() name} should match the mod id.
 */
@ApiStatus.Experimental
public abstract class ModSettings implements Named {
	/**
	 * List of classpath directories, or jar files used to populate the `fabric.classPathGroups` Fabric Loader system property.
	 */
	public abstract ConfigurableFileCollection getModFiles();

	@Inject
	public ModSettings() {
		getModSourceSets().finalizeValueOnRead();
		getModFiles().finalizeValueOnRead();
	}

	/**
	 * Add {@link SourceSet}'s output directories  from the current project to be grouped with the named mod.
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
	 * Add {@link SourceSet}'s output directories from supplied project to be grouped with the named mod.
	 */
	public void sourceSet(SourceSet sourceSet, Project project) {
		getModSourceSets().add(new SourceSetReference(sourceSet, project));
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
	 * List of classpath directories, used to populate the `fabric.classPathGroups` Fabric Loader system property.
	 * Use the {@link ModSettings#sourceSet} methods to add to this.
	 */
	@ApiStatus.Internal
	public abstract ListProperty<SourceSetReference> getModSourceSets();

	@Inject
	public abstract Project getProject();
}
