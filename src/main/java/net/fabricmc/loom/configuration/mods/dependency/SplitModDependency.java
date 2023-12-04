/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022-2023 FabricMC
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
import java.nio.file.Path;
import java.util.Objects;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.ModSettings;
import net.fabricmc.loom.configuration.mods.ArtifactMetadata;
import net.fabricmc.loom.configuration.mods.ArtifactRef;
import net.fabricmc.loom.configuration.mods.JarSplitter;

// Single jar in, 2 out.
public final class SplitModDependency extends ModDependency {
	private final Configuration targetCommonConfig;
	private final Configuration targetClientConfig;
	private final JarSplitter.Target target;
	@Nullable
	private final LocalMavenHelper commonMaven;
	@Nullable
	private final LocalMavenHelper clientMaven;

	public SplitModDependency(ArtifactRef artifact, ArtifactMetadata metadata, String mappingsSuffix, Configuration targetCommonConfig, Configuration targetClientConfig, JarSplitter.Target target, Project project) {
		super(artifact, metadata, mappingsSuffix, project);
		this.targetCommonConfig = Objects.requireNonNull(targetCommonConfig);
		this.targetClientConfig = Objects.requireNonNull(targetClientConfig);
		this.target = Objects.requireNonNull(target);
		this.commonMaven = target.common() ? createMaven(name + "-common") : null;
		this.clientMaven = target.client() ? createMaven(name + "-client") : null;
	}

	@Override
	public boolean isCacheInvalid(Project project, @Nullable String variant) {
		boolean exists = switch (target) {
		case COMMON_ONLY -> getCommonMaven().exists(variant);
		case CLIENT_ONLY -> getClientMaven().exists(variant);
		case SPLIT -> getCommonMaven().exists(variant) && getClientMaven().exists(variant);
		};

		return !exists;
	}

	@Override
	public void copyToCache(Project project, Path path, @Nullable String variant) throws IOException {
		// Split dependencies build with loom 0.12 do not contain the required data to split the sources
		if (target == JarSplitter.Target.SPLIT && variant != null) {
			final JarSplitter.Target artifactTarget = new JarSplitter(path).analyseTarget();

			if (artifactTarget != target) {
				// Found a broken artifact, copy it to both locations without splitting.
				getCommonMaven().copyToMaven(path, variant);
				getClientMaven().copyToMaven(path, variant);
				return;
			}
		}

		switch (target) {
		// Split the jar into 2
		case SPLIT -> {
			final String suffix = variant == null ? "" : "-" + variant;
			final Path commonTempJar = getWorkingFile("common" + suffix);
			final Path clientTempJar = getWorkingFile("client" + suffix);

			final JarSplitter splitter = new JarSplitter(path);
			splitter.split(commonTempJar, clientTempJar);

			getCommonMaven().copyToMaven(commonTempJar, variant);
			getClientMaven().copyToMaven(clientTempJar, variant);
		}

		// No splitting to be done, just copy the input jar to the respective location.
		case CLIENT_ONLY -> getClientMaven().copyToMaven(path, variant);
		case COMMON_ONLY -> getCommonMaven().copyToMaven(path, variant);
		}
	}

	@Override
	public void applyToProject(Project project) {
		if (target.common()) {
			project.getDependencies().add(targetCommonConfig.getName(), getCommonMaven().getNotation());
		}

		if (target.client()) {
			project.getDependencies().add(targetClientConfig.getName(), getClientMaven().getNotation());
		}

		if (target == JarSplitter.Target.SPLIT) {
			createModGroup(
					getCommonMaven().getOutputFile(null),
					getClientMaven().getOutputFile(null)
			);
		}
	}

	private void createModGroup(Path commonJar, Path clientJar) {
		LoomGradleExtension extension = LoomGradleExtension.get(project);
		final ModSettings modSettings = extension.getMods().maybeCreate(String.format("%s-%s-%s", getRemappedGroup(), name, version));
		modSettings.getModFiles().from(
				commonJar.toFile(),
				clientJar.toFile()
		);
	}

	public LocalMavenHelper getCommonMaven() {
		return Objects.requireNonNull(commonMaven, "Cannot get null common maven helper");
	}

	public LocalMavenHelper getClientMaven() {
		return Objects.requireNonNull(clientMaven, "Cannot get null client maven helper");
	}
}
