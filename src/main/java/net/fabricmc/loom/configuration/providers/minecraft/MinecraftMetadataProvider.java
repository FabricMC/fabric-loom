/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2023 FabricMC
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

package net.fabricmc.loom.configuration.providers.minecraft;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.configuration.ConfigContext;
import net.fabricmc.loom.configuration.DependencyInfo;
import net.fabricmc.loom.configuration.providers.minecraft.ManifestLocations.ManifestLocation;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.download.DownloadBuilder;

public final class MinecraftMetadataProvider {
	private final Options options;
	private final Function<String, DownloadBuilder> download;

	private ManifestEntryLocation versionEntry;
	private MinecraftVersionMeta versionMeta;

	private MinecraftMetadataProvider(Options options, Function<String, DownloadBuilder> download) {
		this.options = options;
		this.download = download;
	}

	public static MinecraftMetadataProvider create(ConfigContext configContext) {
		final String minecraftVersion = resolveMinecraftVersion(configContext.project());

		return new MinecraftMetadataProvider(
				MinecraftMetadataProvider.Options.create(
						minecraftVersion,
						configContext.project()
				),
				configContext.extension()::download
		);
	}

	private static String resolveMinecraftVersion(Project project) {
		final DependencyInfo dependency = DependencyInfo.create(project, Constants.Configurations.MINECRAFT);
		return dependency.getDependency().getVersion();
	}

	public String getMinecraftVersion() {
		return options.minecraftVersion();
	}

	public MinecraftVersionMeta getVersionMeta() {
		try {
			if (versionEntry == null) {
				versionEntry = getVersionEntry();
			}

			if (versionMeta == null) {
				versionMeta = readVersionMeta();
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e.getMessage(), e);
		}

		return versionMeta;
	}

	private ManifestEntryLocation getVersionEntry() throws IOException {
		// Custom URL always takes priority
		if (options.customManifestUrl() != null) {
			VersionsManifest.Version customVersion = new VersionsManifest.Version();
			customVersion.id = options.minecraftVersion();
			customVersion.url = options.customManifestUrl();
			return new ManifestEntryLocation(null, customVersion);
		}

		final List<ManifestEntrySupplier> suppliers = new ArrayList<>();

		// First try finding the version with caching
		for (ManifestLocation location : options.versionsManifests()) {
			suppliers.add(() -> getManifestEntry(location, false));
		}

		// Then force download the manifest to find the version
		for (ManifestLocation location : options.versionsManifests()) {
			suppliers.add(() -> getManifestEntry(location, true));
		}

		for (ManifestEntrySupplier supplier : suppliers) {
			final ManifestEntryLocation version = supplier.get();

			if (version != null) {
				return version;
			}
		}

		throw new RuntimeException("Failed to find minecraft version: " + options.minecraftVersion());
	}

	private ManifestEntryLocation getManifestEntry(ManifestLocation location, boolean forceDownload) throws IOException {
		DownloadBuilder builder = download.apply(location.url());

		if (forceDownload) {
			builder = builder.forceDownload();
		} else {
			builder = builder.defaultCache();
		}

		final Path cacheFile = location.cacheFile(options.userCache());
		final String versionManifest = builder.downloadString(cacheFile);
		final VersionsManifest manifest = LoomGradlePlugin.GSON.fromJson(versionManifest, VersionsManifest.class);
		final VersionsManifest.Version version = manifest.getVersion(options.minecraftVersion());

		if (version != null) {
			return new ManifestEntryLocation(location, version);
		}

		return null;
	}

	private MinecraftVersionMeta readVersionMeta() throws IOException {
		final DownloadBuilder builder = download.apply(versionEntry.entry.url);

		if (versionEntry.entry.sha1 != null) {
			builder.sha1(versionEntry.entry.sha1);
		} else {
			builder.defaultCache();
		}

		final String fileName = getVersionMetaFileName();
		final Path cacheFile = options.workingDir().resolve(fileName);
		final String json = builder.downloadString(cacheFile);
		return LoomGradlePlugin.GSON.fromJson(json, MinecraftVersionMeta.class);
	}

	private String getVersionMetaFileName() {
		String base = "minecraft-info";

		// custom version metadata
		if (versionEntry.manifest == null) {
			return base + Integer.toHexString(versionEntry.entry.url.hashCode()) + ".json";
		}

		// custom versions manifest
		if (!versionEntry.manifest.isBuiltIn()) {
			return base + Integer.toHexString(versionEntry.manifest.url().hashCode()) + ".json";
		}

		return base + ".json";
	}

	public record Options(String minecraftVersion,
					ManifestLocations versionsManifests,
					@Nullable String customManifestUrl,
					Path userCache,
					Path workingDir) {
		public static Options create(String minecraftVersion, Project project) {
			final LoomGradleExtension extension = LoomGradleExtension.get(project);
			final Path userCache = extension.getFiles().getUserCache().toPath();
			final Path workingDir = MinecraftProvider.minecraftWorkingDirectory(project, minecraftVersion).toPath();

			final ManifestLocations manifestLocations = extension.getVersionsManifests();
			final Property<String> customMetaUrl = extension.getCustomMinecraftMetadata();

			return new Options(
					minecraftVersion,
					manifestLocations,
					customMetaUrl.getOrNull(),
					userCache,
					workingDir
			);
		}
	}

	@FunctionalInterface
	private interface ManifestEntrySupplier {
		ManifestEntryLocation get() throws IOException;
	}

	private record ManifestEntryLocation(ManifestLocation manifest, VersionsManifest.Version entry) {
	}
}
