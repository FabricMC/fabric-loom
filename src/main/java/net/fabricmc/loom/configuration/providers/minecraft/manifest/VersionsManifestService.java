/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2024 FabricMC
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

package net.fabricmc.loom.configuration.providers.minecraft.manifest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;
import org.gradle.api.Project;
import org.jetbrains.annotations.VisibleForTesting;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.api.manifest.VersionsManifestProvider;
import net.fabricmc.loom.util.service.SharedService;
import net.fabricmc.loom.util.service.SharedServiceManager;

public final class VersionsManifestService implements SharedService {
	private final Path versionsManifestJson;
	private final Path experimentalVersionsManifestJson;
	private final Supplier<VersionsManifest> versionsManifest = Suppliers.memoize(this::createVersionsManifest);
	private final Supplier<VersionsManifest> experimentalVersionsManifest = Suppliers.memoize(this::createExperimentalVersionsManifest);

	private VersionsManifestService(Path versionsManifestJson, Path experimentalVersionsManifestJson) {
		this.versionsManifestJson = versionsManifestJson;
		this.experimentalVersionsManifestJson = experimentalVersionsManifestJson;
	}

	public static synchronized VersionsManifestService getInstance(SharedServiceManager sharedServiceManager, Project project) {
		final LoomGradleExtension extension = LoomGradleExtension.get(project);
		final VersionsManifestProvider manifestProvider = extension.getVersionsManifestProvider();
		final String id = "VersionsManifestService:%s".formatted(manifestProvider.getName());

		return sharedServiceManager.getOrCreateService(id, () -> create(manifestProvider, extension, project));
	}

	@VisibleForTesting
	public static VersionsManifestService create(VersionsManifestProvider versionsManifestProvider, LoomGradleExtension extension, Project project) {
		final Path userCache = extension.getFiles().getUserCache().toPath();
		final Path manifestJson = userCache.resolve(versionsManifestProvider.getName() + ".json");
		final Path experimentalManifestJson = userCache.resolve("experimental_" + versionsManifestProvider.getName() + ".json");

		try {
			versionsManifestProvider.provide(manifestJson, experimentalManifestJson);
		} catch (IOException e) {
			try {
				Files.deleteIfExists(manifestJson);
				Files.deleteIfExists(experimentalManifestJson);
			} catch (IOException ex) {
				ex.printStackTrace();
			}

			throw new UncheckedIOException("Failed to provide versions manifest", e);
		}

		return new VersionsManifestService(manifestJson, experimentalManifestJson);
	}

	private VersionsManifest createVersionsManifest() {
		try (BufferedReader br = Files.newBufferedReader(versionsManifestJson)) {
			return LoomGradlePlugin.GSON.fromJson(br, VersionsManifest.class);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to read versions manifest", e);
		}
	}

	private VersionsManifest createExperimentalVersionsManifest() {
		try (BufferedReader br = Files.newBufferedReader(experimentalVersionsManifestJson)) {
			return LoomGradlePlugin.GSON.fromJson(br, VersionsManifest.class);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to read experimental versions manifest", e);
		}
	}

	public VersionsManifest getVersionsManifest() {
		return versionsManifest.get();
	}

	public VersionsManifest getExperimentalVersionsManifest() {
		return experimentalVersionsManifest.get();
	}

	public Path getVersionsManifestJson() {
		return Objects.requireNonNull(versionsManifestJson, "Versions manifest has not been setup");
	}

	public Path getVersionsExperimentalManifestJson() {
		return Objects.requireNonNull(experimentalVersionsManifestJson, "Experimental versions manifest has not been setup");
	}
}
