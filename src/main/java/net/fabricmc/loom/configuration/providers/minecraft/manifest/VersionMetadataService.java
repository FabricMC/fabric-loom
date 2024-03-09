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

import org.gradle.api.Project;
import org.jetbrains.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.api.manifest.VersionMetadataProvider;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftProvider;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftVersionMeta;
import net.fabricmc.loom.util.service.SharedService;
import net.fabricmc.loom.util.service.SharedServiceManager;

public final class VersionMetadataService implements SharedService {
	private final Path versionMetadataJson;
	private final Supplier<MinecraftVersionMeta> versionMetadata = Suppliers.memoize(this::createVersionMetadata);

	private VersionMetadataService(Path versionMetadataJson) {
		this.versionMetadataJson = versionMetadataJson;
	}

	public static synchronized VersionMetadataService getInstance(SharedServiceManager sharedServiceManager, Project project, MinecraftProvider minecraftProvider) {
		final LoomGradleExtension extension = LoomGradleExtension.get(project);
		final VersionMetadataProvider metadataProvider = extension.getVersionMetadataProvider();
		final String id = "VersionMetadataService:%s:%s".formatted(metadataProvider.getName(), minecraftProvider.minecraftVersion());

		return sharedServiceManager.getOrCreateService(id, () -> create(metadataProvider, extension, project));
	}

	@VisibleForTesting
	public static VersionMetadataService create(VersionMetadataProvider versionMetadataProvider, LoomGradleExtension extension, Project project) {
		final Path userCache = extension.getFiles().getUserCache().toPath();
		final Path metadataJson = userCache.resolve(versionMetadataProvider.getName() + ".json");

		try {
			versionMetadataProvider.provide(metadataJson);
		} catch (IOException e) {
			try {
				Files.deleteIfExists(metadataJson);
			} catch (IOException ex) {
				ex.printStackTrace();
			}

			throw new UncheckedIOException("Failed to provide version metadata", e);
		}

		return new VersionMetadataService(metadataJson);
	}

	private MinecraftVersionMeta createVersionMetadata() {
		try (BufferedReader br = Files.newBufferedReader(versionMetadataJson)) {
			return LoomGradlePlugin.GSON.fromJson(br, MinecraftVersionMeta.class);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to read version metadata", e);
		}
	}

	public MinecraftVersionMeta getVersionMetadata() {
		return versionMetadata.get();
	}

	public Path getVersionsManifestJson() {
		return Objects.requireNonNull(versionMetadataJson, "Version metadata has not been setup");
	}
}
