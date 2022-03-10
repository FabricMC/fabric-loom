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

package net.fabricmc.loom.util.gradle;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.gradle.api.Project;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetOutput;

import net.fabricmc.loom.api.ModSettings;

public final class SourceSetsHelper {
	private SourceSetsHelper() {
	}

	public static List<File> getClasspath(ModSettings modSettings, Project project) {
		return modSettings.getSourceSets().get().stream()
				.flatMap(sourceSet -> getClasspath(sourceSet, project).stream())
				.toList();
	}

	public static List<File> getClasspath(SourceSet sourceSet, Project project) {
		final List<File> classpath = getGradleClasspath(sourceSet);

		classpath.addAll(getIdeaClasspath(sourceSet, project));
		classpath.addAll(getEclipseClasspath(sourceSet, project));

		return classpath;
	}

	private static List<File> getGradleClasspath(SourceSet sourceSet) {
		final SourceSetOutput output = sourceSet.getOutput();
		final File resources = output.getResourcesDir();

		final List<File> classpath = new ArrayList<>();

		classpath.addAll(output.getClassesDirs().getFiles());

		if (resources != null) {
			classpath.add(resources);
		}

		return classpath;
	}

	private static List<File> getIdeaClasspath(SourceSet sourceSet, Project project) {
		// TODO
		return Collections.emptyList();
	}

	private static List<File> getEclipseClasspath(SourceSet sourceSet, Project project) {
		// TODO
		return Collections.emptyList();
	}
}
