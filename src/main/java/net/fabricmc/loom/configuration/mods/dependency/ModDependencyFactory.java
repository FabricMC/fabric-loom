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

package net.fabricmc.loom.configuration.mods.dependency;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.mods.ArtifactMetadata;
import net.fabricmc.loom.configuration.mods.ArtifactRef;
import net.fabricmc.loom.configuration.mods.JarSplitter;
import net.fabricmc.loom.util.AttributeHelper;

public class ModDependencyFactory {
	private static final String TARGET_ATTRIBUTE_KEY = "loom-target";

	public static ModDependency create(ArtifactRef artifact, ArtifactMetadata metadata, Configuration targetConfig, @Nullable Configuration targetClientConfig, String mappingsSuffix, Project project) {
		if (targetClientConfig != null && LoomGradleExtension.get(project).getSplitModDependencies().get()) {
			final Optional<JarSplitter.Target> cachedTarget = readTarget(artifact);
			JarSplitter.Target target;

			if (cachedTarget.isPresent()) {
				target = cachedTarget.get();
			} else {
				target = new JarSplitter(artifact.path()).analyseTarget();
				writeTarget(artifact, target);
			}

			if (target != null) {
				return new SplitModDependency(artifact, metadata, mappingsSuffix, targetConfig, targetClientConfig, target, project);
			}
		}

		return new SimpleModDependency(artifact, metadata, mappingsSuffix, targetConfig, project);
	}

	private static Optional<JarSplitter.Target> readTarget(ArtifactRef artifact) {
		try {
			return AttributeHelper.readAttribute(artifact.path(), TARGET_ATTRIBUTE_KEY).map(s -> {
				if ("null".equals(s)) {
					return null;
				}

				return JarSplitter.Target.valueOf(s);
			});
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to read artifact target attribute", e);
		}
	}

	private static void writeTarget(ArtifactRef artifact, JarSplitter.Target target) {
		final String value = target != null ? target.name() : "null";

		try {
			AttributeHelper.writeAttribute(artifact.path(), TARGET_ATTRIBUTE_KEY, value);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to write artifact target attribute", e);
		}
	}
}
