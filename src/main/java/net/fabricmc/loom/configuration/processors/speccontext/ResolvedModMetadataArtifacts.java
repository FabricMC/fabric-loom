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
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.gradle.api.artifacts.ResolvedArtifact;

record ResolvedModMetadataArtifacts(Map<ModMetadataArtifactType, List<ResolvedArtifact>> artifactsByType) {
	ResolvedModMetadataArtifacts {
		Map<ModMetadataArtifactType, List<ResolvedArtifact>> copy = new EnumMap<>(ModMetadataArtifactType.class);

		for (var entry : artifactsByType.entrySet()) {
			copy.put(entry.getKey(), List.copyOf(entry.getValue()));
		}

		artifactsByType = Collections.unmodifiableMap(copy);
	}

	ResolvedModMetadataArtifacts(List<ResolvedArtifact> artifacts) {
		this(indexByType(artifacts));
	}

	private static Map<ModMetadataArtifactType, List<ResolvedArtifact>> indexByType(List<ResolvedArtifact> artifacts) {
		Map<ModMetadataArtifactType, List<ResolvedArtifact>> indexed = new EnumMap<>(ModMetadataArtifactType.class);

		for (ResolvedArtifact artifact : artifacts) {
			String artifactType = artifact.getType();

			for (ModMetadataArtifactType type : ModMetadataArtifactType.values()) {
				if (type.value().equals(artifactType)) {
					indexed.computeIfAbsent(type, key -> new ArrayList<>()).add(artifact);
					break;
				}
			}
		}

		return indexed;
	}

	Optional<ResolvedArtifact> findFirst(ModMetadataArtifactType type) {
		return artifactsByType.getOrDefault(type, List.of()).stream().findFirst();
	}

	List<ResolvedArtifact> getAll(ModMetadataArtifactType... types) {
		EnumSet<ModMetadataArtifactType> requestedTypes = EnumSet.noneOf(ModMetadataArtifactType.class);
		Collections.addAll(requestedTypes, types);
		List<ResolvedArtifact> matching = new ArrayList<>();

		for (ModMetadataArtifactType type : requestedTypes) {
			matching.addAll(artifactsByType.getOrDefault(type, List.of()));
		}

		return List.copyOf(matching);
	}
}
