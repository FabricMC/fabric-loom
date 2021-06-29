/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016, 2017, 2018 FabricMC
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

package net.fabricmc.loom;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableMap;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.jetbrains.annotations.NotNull;

public class MixinGradleExtension {
	private Project project;
	private Map<SourceSet, String> refmapNames;			// record sourceSet -> refMap name

	public MixinGradleExtension(Project project) {
		this.project = project;
		this.refmapNames = new HashMap<>();
	}

	/**
	 * Apply Mixin AP to sourceSet.
	 * @param sourceSet the sourceSet that applies Mixin AP
	 * @param refmapName the output ref-map name
	 */
	public void add(SourceSet sourceSet, String refmapName) {
		refmapNames.put(sourceSet, refmapName);
	}

	public void add(String sourceSetName, String refmapName) {
		// try to find sourceSet with name sourceSetName in this project
		SourceSet sourceSet = project.getConvention().getPlugin(JavaPluginConvention.class)
				.getSourceSets().findByName(sourceSetName);

		if (sourceSet == null) {
			// no sourceSet with name sourceSetName found in this project, let's try to find it in
			// across all projects
			Stream<SourceSet> stream = project.getRootProject().getAllprojects().stream()
					.map(proj -> proj.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().findByName(sourceSetName));
			sourceSet = switch ((int) stream.count()) {
			// still no sourceSet found
			case 0 -> throw new InvalidUserDataException("No sourceSet " + sourceSetName + " was found");
			case 1 -> stream.findFirst().orElseThrow();
			// multiple sourceSet name found
			default -> throw new InvalidUserDataException("Ambiguous sourceSet name " + sourceSetName);
			};
		}

		add(sourceSet, refmapName);
	}

	/**
	 * Apply Mixin AP to sourceSet with output ref-map name equal to {@code loom.refmapName}.
	 * @param sourceSet the sourceSet that applies Mixin AP
	 */
	public void add(SourceSet sourceSet) {
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		add(sourceSet, extension.getRefmapName());
	}

	public void add(String sourceSetName) {
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		add(sourceSetName, extension.getRefmapName());
	}

	@NotNull
	public Project getProjectFromSourceSet(SourceSet sourceSet) {
		Project result = project.getRootProject().getAllprojects().stream()
				.filter(proj -> proj.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().contains(sourceSet))
				.findFirst().orElse(null);

		if (result == null) {
			throw new RuntimeException("Cannot find Project corresponding to SourceSet " + sourceSet.getName());
		}

		return result;
	}

	@NotNull
	public Map<SourceSet, String> getRefmapNames() {
		return ImmutableMap.copyOf(refmapNames);
	}

	public boolean isEmpty() {
		return refmapNames.isEmpty();
	}
}
