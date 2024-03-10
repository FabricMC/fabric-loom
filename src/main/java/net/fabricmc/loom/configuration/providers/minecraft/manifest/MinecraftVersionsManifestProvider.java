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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.gradle.api.provider.Property;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.api.manifest.VersionsManifestProvider;

@ApiStatus.Internal
public abstract class MinecraftVersionsManifestProvider extends VersionsManifestProvider {
	public static final String NAME = "versions_manifest";
	private static final Logger LOGGER = LoggerFactory.getLogger(VersionsManifestProvider.class);

	public abstract Property<String> getVersionsManifestUrl();

	public abstract Property<String> getExperimentalVersionsManifestUrl();

	public abstract Property<Boolean> getRefreshDeps();

	@Override
	public void provide(Path versionsManifest, Path experimentalVersionsManifest) throws IOException {
		if (!Files.exists(versionsManifest) || getRefreshDeps().get()) {
			final String url = getVersionsManifestUrl().get();

			LOGGER.info("Downloading versions manifest from {}", url);

			Files.deleteIfExists(versionsManifest);

			getDownloader().get().apply(url)
					.defaultCache()
					.downloadPath(versionsManifest);
		}

		if (!Files.exists(experimentalVersionsManifest) || getRefreshDeps().get()) {
			if (getExperimentalVersionsManifestUrl().isPresent()) {
				final String url = getExperimentalVersionsManifestUrl().get();

				LOGGER.info("Downloading experimental versions manifest from {}", url);

				Files.deleteIfExists(experimentalVersionsManifest);

				getDownloader().get().apply(url)
						.defaultCache()
						.downloadPath(experimentalVersionsManifest);
			}
		}
	}

	@Override
	public @NotNull String getName() {
		return NAME;
	}
}
