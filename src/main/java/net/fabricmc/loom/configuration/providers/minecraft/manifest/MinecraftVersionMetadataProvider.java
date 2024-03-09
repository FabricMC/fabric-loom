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

import net.fabricmc.loom.api.manifest.VersionMetadataProvider;

@ApiStatus.Internal
public abstract class MinecraftVersionMetadataProvider extends VersionMetadataProvider {
	public static final String NAME = "minecraft-info";
	private static final Logger LOGGER = LoggerFactory.getLogger(VersionMetadataProvider.class);

	public abstract Property<String> getVersionMetadataUrl();

	public abstract Property<VersionsManifest> getVersionsManifest();

	public abstract Property<VersionsManifest> getExperimentalVersionsManifest();

	public abstract Property<Boolean> getRefreshDeps();

	@Override
	public void provide(Path versionMetadata) throws IOException {
		if (Files.exists(versionMetadata) && !getRefreshDeps().get()) {
			return;
		}

		if (getVersionMetadataUrl().isPresent()) {
			final String url = getVersionMetadataUrl().get();

			LOGGER.info("Downloading custom version metadata from {}", url);

			getDownloader().get().apply(url)
					.defaultCache()
					.downloadPath(versionMetadata);
		} else {
			String minecraftVersion = getMinecraftVersion().get();
			VersionsManifest manifest = getVersionsManifest().get();
			VersionsManifest experimentalManifest = getExperimentalVersionsManifest().get();

			VersionsManifest.Version version = manifest.getVersion(minecraftVersion);

			if (version == null && experimentalManifest != null) {
				version = experimentalManifest.getVersion(minecraftVersion);
			}

			if (version == null) {
				throw new RuntimeException("Failed to find minecraft version: " + minecraftVersion);
			}

			LOGGER.info("Downloading version metadata from {}", version.url);

			getDownloader().get().apply(version.url)
					.defaultCache()
					.downloadPath(versionMetadata);
		}
	}

	@Override
	public @NotNull String getName() {
		return NAME;
	}
}
