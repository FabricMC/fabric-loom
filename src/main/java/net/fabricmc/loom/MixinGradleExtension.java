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

import com.google.common.collect.ImmutableMap;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;

public class MixinGradleExtension {
	private Project project;
	private Map<SourceSet, String> refmapNames;			// record sourceSet -> refMap name

	public MixinGradleExtension(Project project) {
		this.project = project;
		this.refmapNames = new HashMap<>();
	}

	/**
	 * Apply Mixin AP to sourceSet
	 * @param sourceSet the sourceSet that applies Mixin AP
	 * @param refmapName the output ref-map name
	 */
	public void add(SourceSet sourceSet, String refmapName) {
		refmapNames.put(sourceSet, refmapName);
	}

	public void add(String sourceSetName, String refmapName) {
		SourceSet sourceSet = project.getConvention().getPlugin(JavaPluginConvention.class)
				.getSourceSets().findByName(sourceSetName);
		if (sourceSet == null) {
			throw new InvalidUserDataException("No sourceSet " + sourceSetName + " was found");
		}
		add(sourceSet, refmapName);
	}

	/**
	 * Apply Mixin AP to sourceSet with output ref-map name equal to {@code loom.refmapName}
	 * @param sourceSet the sourceSet that applies Mixin AP
	 */
	public void add(SourceSet sourceSet) {
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		add(sourceSet, extension.getRefmapName());
	}

	public void add(String sourceSetName) {
		SourceSet sourceSet = project.getConvention().getPlugin(JavaPluginConvention.class)
				.getSourceSets().findByName(sourceSetName);
		if (sourceSet == null) {
			throw new InvalidUserDataException("No sourceSet " + sourceSetName + " was found");
		}
		add(sourceSet);
	}

	public Map<SourceSet, String> getRefmapNames() {
		return ImmutableMap.copyOf(refmapNames);
	}

	public boolean isEmpty() {
		return refmapNames.isEmpty();
	}
}
