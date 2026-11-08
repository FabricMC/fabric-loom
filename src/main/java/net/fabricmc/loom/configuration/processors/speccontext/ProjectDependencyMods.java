/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2026 FabricMC
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

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.loom.api.processor.SpecContext;
import net.fabricmc.loom.util.fmj.FabricModJson;

/// Project dependency metadata classified by classpath membership.
///
/// @param all mods on any main or client compile or runtime classpath
/// @param compileRuntime mods on both compile and runtime classpaths within either source set
/// @param clientOnly mods on both client classpaths but not both main classpaths; a subset of `compileRuntime`
record ProjectDependencyMods(List<FabricModJson> all, List<FabricModJson> compileRuntime, List<FabricModJson> clientOnly) {
	static ProjectDependencyMods create(ProjectView projectView) {
		if (projectView.disableProjectDependantMods()) {
			return new ProjectDependencyMods(List.of(), List.of(), List.of());
		}

		List<FabricModJson> mainCompile = projectView.getProjectMods(ProjectView.ArtifactUsage.COMPILE, DebofConfiguration.TargetSourceSet.MAIN);
		List<FabricModJson> mainRuntime = projectView.getProjectMods(ProjectView.ArtifactUsage.RUNTIME, DebofConfiguration.TargetSourceSet.MAIN);
		List<FabricModJson> all = new ArrayList<>(mainCompile);
		all.addAll(mainRuntime);
		List<FabricModJson> common = intersection(mainCompile, mainRuntime);
		List<FabricModJson> clientOnly = new ArrayList<>();

		if (projectView.areEnvironmentSourceSetsSplit()) {
			List<FabricModJson> clientCompile = projectView.getProjectMods(ProjectView.ArtifactUsage.COMPILE, DebofConfiguration.TargetSourceSet.CLIENT);
			List<FabricModJson> clientRuntime = projectView.getProjectMods(ProjectView.ArtifactUsage.RUNTIME, DebofConfiguration.TargetSourceSet.CLIENT);
			all.addAll(clientCompile);
			all.addAll(clientRuntime);
			clientOnly.addAll(intersection(clientCompile, clientRuntime));
			clientOnly.removeAll(common);
		}

		List<FabricModJson> compileRuntime = new ArrayList<>(common);
		compileRuntime.addAll(clientOnly);
		return new ProjectDependencyMods(
				SpecContext.distinctSorted(all),
				SpecContext.distinctSorted(compileRuntime),
				SpecContext.distinctSorted(clientOnly)
		);
	}

	private static List<FabricModJson> intersection(List<FabricModJson> compile, List<FabricModJson> runtime) {
		List<FabricModJson> common = new ArrayList<>(compile);
		common.retainAll(runtime);
		return common;
	}
}
