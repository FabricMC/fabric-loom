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

package net.fabricmc.loom.configuration.ide;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.gradle.api.Project;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedModuleVersion;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.RunConfiguration;
import net.fabricmc.loom.configuration.providers.BundleMetadata;
import net.fabricmc.loom.util.Constants;

public class RuntimeLibraries {
	public static List<String> getExcludedLibraryPaths(Project project, RunConfiguration runConfiguration) {
		if (!runConfiguration.getRuntimeEnvironment().get().toLowerCase(Locale.ROOT).equals("server")) {
			return Collections.emptyList();
		}

		final BundleMetadata bundleMetadata = LoomGradleExtension.get(project).getMinecraftProvider().getServerBundleMetadata();

		if (bundleMetadata == null) {
			// Legacy version
			return Collections.emptyList();
		}

		final Set<ResolvedArtifact> clientLibraries = getArtifacts(project, Constants.Configurations.MINECRAFT_CLIENT_RUNTIME_LIBRARIES);
		final Set<ResolvedArtifact> serverLibraries = getArtifacts(project, Constants.Configurations.MINECRAFT_SERVER_RUNTIME_LIBRARIES);
		final List<String> clientOnlyLibraries = new ArrayList<>();

		for (ResolvedArtifact library : clientLibraries) {
			if (!containsLibrary(serverLibraries, library.getModuleVersion().getId())) {
				clientOnlyLibraries.add(library.getFile().getAbsolutePath());
			}
		}

		return clientOnlyLibraries;
	}

	private static Set<ResolvedArtifact> getArtifacts(Project project, String configuration) {
		return project.getConfigurations().getByName(configuration).getHierarchy()
				.stream()
				.map(c -> c.getResolvedConfiguration().getResolvedArtifacts())
				.flatMap(Collection::stream)
				.collect(Collectors.toSet());
	}

	private static boolean containsLibrary(Set<ResolvedArtifact> artifacts, ModuleVersionIdentifier identifier) {
		return artifacts.stream()
				.map(ResolvedArtifact::getModuleVersion)
				.map(ResolvedModuleVersion::getId)
				.anyMatch(test -> test.getGroup().equals(identifier.getGroup()) && test.getName().equals(identifier.getName()));
	}
}
