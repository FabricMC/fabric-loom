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

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.configuration.mods.ArtifactRef;
import net.fabricmc.loom.configuration.mods.JarSplitter;

public class ModDependencyFactory {
	public static ModDependency create(ArtifactRef artifact, Configuration targetConfig, @Nullable Configuration targetClientConfig, String mappingsSuffix, Project project) {
		// TODO this is hot code, it must be fast speed me up.
		if (targetClientConfig != null && new JarSplitter(artifact.path()).canSplit()) {
			// TODO support using simple for common or client only jars that were built with split jars enabled.
			return new SplitModDependency(artifact, mappingsSuffix, targetConfig, targetClientConfig, project);
		}

		return new SimpleModDependency(artifact, mappingsSuffix, targetConfig, project);
	}
}
