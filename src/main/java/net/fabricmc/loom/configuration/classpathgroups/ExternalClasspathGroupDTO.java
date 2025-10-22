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

package net.fabricmc.loom.configuration.classpathgroups;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.gradle.api.Project;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;

import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.util.gradle.SourceSetHelper;
import net.fabricmc.loom.util.gradle.SourceSetReference;

/**
 * This object is exported by projects as a json file to be consumed by others to correctly populate the classpath groups.
 */
public record ExternalClasspathGroupDTO(String projectPath, Map<String, List<String>> classpaths) implements Serializable {
	public static ExternalClasspathGroupDTO createFromProject(Project project) {
		SourceSetContainer sourceSets = SourceSetHelper.getSourceSets(project);

		Map<String, List<String>> classpaths = new HashMap<>();

		for (SourceSet sourceSet : sourceSets) {
			SourceSetReference ref = new SourceSetReference(sourceSet, project);
			List<File> classpath = SourceSetHelper.getClasspath(ref, true);
			classpaths.put(sourceSet.getName(), classpath.stream().map(File::getAbsolutePath).toList());
		}

		return new ExternalClasspathGroupDTO(project.getPath(), Collections.unmodifiableMap(classpaths));
	}

	public static Map<String, ExternalClasspathGroupDTO> resolveExternal(Set<File> files) {
		Map<String, ExternalClasspathGroupDTO> map = new HashMap<>();

		for (File file : files) {
			String json;

			try {
				json = Files.readString(file.toPath());
			} catch (IOException e) {
				throw new UncheckedIOException("Failed to read external classpath group file: " + file, e);
			}

			ExternalClasspathGroupDTO dto = LoomGradlePlugin.GSON.fromJson(json, ExternalClasspathGroupDTO.class);
			map.put(dto.projectPath(), dto);
		}

		return Collections.unmodifiableMap(map);
	}

	public List<String> getForSourceSet(String sourceSetName) {
		return Objects.requireNonNull(classpaths.get(sourceSetName), "No classpath found for source set: " + sourceSetName);
	}
}
