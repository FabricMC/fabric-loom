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

package net.fabricmc.loom.api.processor;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;

import net.fabricmc.loom.configuration.processors.speccontext.ProjectView;
import net.fabricmc.loom.util.fmj.FabricModJson;

public interface SpecContext {
	/**
	 * Returns a list of all the external mods that this project depends on regardless of configuration.
	 */
	List<FabricModJson> modDependencies();

	List<FabricModJson> localMods();

	/**
	 * Return a set of mods that should be used for transforms, that target EITHER the common or client.
	 */
	List<FabricModJson> modDependenciesCompileRuntime();

	/**
	 * Return a set of mods that should be used for transforms, that target ONLY the client.
	 */
	List<FabricModJson> modDependenciesCompileRuntimeClient();

	default List<FabricModJson> allMods() {
		return Stream.concat(modDependencies().stream(), localMods().stream()).toList();
	}

	// Returns all of the loom projects that are depended on in the main sourceset
	// TODO make project isolation aware
	static Stream<Project> getDependentProjects(ProjectView projectView) {
		final Stream<Project> runtimeProjects = projectView.getLoomProjectDependencies(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME);
		final Stream<Project> compileProjects = projectView.getLoomProjectDependencies(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME);

		return Stream.concat(runtimeProjects, compileProjects)
				.distinct();
	}

	// Sort to ensure stable caching
	static List<FabricModJson> distinctSorted(List<FabricModJson> mods) {
		return mods.stream()
				.distinct()
				.sorted(Comparator.comparing(FabricModJson::getId))
				.toList();
	}
}
