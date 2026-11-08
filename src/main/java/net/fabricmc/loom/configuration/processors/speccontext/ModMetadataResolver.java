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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.attributes.Usage;

import net.fabricmc.loom.util.fmj.FabricModJson;
import net.fabricmc.loom.util.fmj.FabricModJsonFactory;
import net.fabricmc.loom.util.fmj.FabricModJsonSource;

/// Resolves source mod metadata without accessing the producing project's Gradle model.
/// Reuse one instance across classpath resolutions within a single project configuration.
public final class ModMetadataResolver {
	private final Project project;
	private final Map<ProjectComponentIdentifier, FabricModJson> metadataCache = new HashMap<>();

	public ModMetadataResolver(Project project) {
		this.project = project;
	}

	public List<FabricModJson> resolve(Configuration configuration) {
		Set<ResolvedArtifact> artifacts = resolveArtifacts(configuration);
		return parseArtifacts(artifacts);
	}

	private Set<ResolvedArtifact> resolveArtifacts(Configuration source) {
		Configuration metadataConfiguration = project.getConfigurations().detachedConfiguration();
		metadataConfiguration.setTransitive(false);
		metadataConfiguration.attributes(attributes -> attributes.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, ModMetadata.USAGE)));

		for (ModuleDependency dependency : source.getAllDependencies().withType(ModuleDependency.class)) {
			ModuleDependency copy = dependency.copy();
			copy.getArtifacts().clear();

			if (copy.getTargetConfiguration() != null) {
				copy.setTargetConfiguration(null);
			}

			metadataConfiguration.getDependencies().add(copy);
		}

		return metadataConfiguration.getResolvedConfiguration().getLenientConfiguration().getArtifacts();
	}

	private List<FabricModJson> parseArtifacts(Set<ResolvedArtifact> artifacts) {
		Map<ProjectComponentIdentifier, List<ResolvedArtifact>> components = new LinkedHashMap<>();

		for (ResolvedArtifact artifact : artifacts) {
			ComponentIdentifier component = artifact.getId().getComponentIdentifier();

			if (component instanceof ProjectComponentIdentifier projectComponent) {
				components.computeIfAbsent(projectComponent, key -> new ArrayList<>()).add(artifact);
			}
		}

		List<FabricModJson> mods = new ArrayList<>();

		for (var entry : components.entrySet()) {
			var componentArtifacts = new ResolvedModMetadataArtifacts(entry.getValue());
			ResolvedArtifact modJson = componentArtifacts.findFirst(ModMetadataArtifactType.METADATA).orElse(null);

			if (modJson == null) {
				continue;
			}

			FabricModJson mod = metadataCache.computeIfAbsent(entry.getKey(), key -> readModMetadata(modJson, componentArtifacts));
			mods.add(mod);
		}

		return List.copyOf(mods);
	}

	private FabricModJson readModMetadata(ResolvedArtifact modJson, ResolvedModMetadataArtifacts artifacts) {
		Path metadataFile = modJson.getFile().toPath();
		Map<String, Path> resources = new LinkedHashMap<>();
		resources.put(FabricModJsonFactory.FABRIC_MOD_JSON, metadataFile);

		for (ResolvedArtifact artifact : artifacts.getAll(ModMetadataArtifactType.CLASS_TWEAKER, ModMetadataArtifactType.PROVIDED_JAVADOC)) {
			Path resource = artifact.getFile().toPath();
			resources.put(artifact.getClassifier(), resource);
			ModMetadata.readConfigurationInput(project, resource);
		}

		var source = new FabricModJsonSource.FileMapSource(resources);
		return ModMetadata.readModJson(project, metadataFile, source);
	}
}
