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
import java.util.List;
import java.util.function.Function;

import org.gradle.api.Project;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.configuration.ConfigContext;
import net.fabricmc.loom.configuration.DependencyInfo;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.MirrorUtil;
import net.fabricmc.loom.util.download.DownloadBuilder;

public final class MinecraftMetadataProvider {
	private final Options options;
	private final Function<String, DownloadBuilder> download;

	private ManifestVersion.Versions versionEntry;
	private MinecraftVersionMeta versionMeta;

	private MinecraftMetadataProvider(Options options, Function<String, DownloadBuilder> download) {
		this.options = options;
		this.download = download;
	}

	public static MinecraftMetadataProvider create(ConfigContext configContext) {
		final String minecraftVersion = resolveMinecraftVersion(configContext.project());
		final Path workingDir = MinecraftProvider.minecraftWorkingDirectory(configContext.project(), minecraftVersion).toPath();

		return new MinecraftMetadataProvider(
				MinecraftMetadataProvider.Options.create(
						minecraftVersion,
						configContext.project(),
						workingDir.resolve("minecraft-info.json")
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

	private ManifestVersion.Versions getVersionEntry() throws IOException {
		// Custom URL always takes priority
		if (options.customManifestUrl() != null) {
			ManifestVersion.Versions customVersion = new ManifestVersion.Versions();
			customVersion.id = options.minecraftVersion();
			customVersion.url = options.customManifestUrl();
			return customVersion;
		}

		final List<ManifestVersionSupplier> suppliers = List.of(
				// First try finding the version with caching
				() -> getVersions(false),
				// Then try finding the experimental version with caching
				() -> getExperimentalVersions(false),
				// Then force download Mojang's metadata to find the version
				() -> getVersions(true),
				// Finally try a force downloaded experimental metadata.
				() -> getExperimentalVersions(true)
		);

		for (ManifestVersionSupplier supplier : suppliers) {
			final ManifestVersion.Versions version = supplier.get().getVersion(options.minecraftVersion());

			if (version != null) {
				return version;
			}
		}

		throw new RuntimeException("Failed to find minecraft version: " + options.minecraftVersion());
	}

	private ManifestVersion getVersions(boolean forceDownload) throws IOException {
		return getVersions(options.versionManifestUrl(), options.versionManifestPath(), forceDownload);
	}

	private ManifestVersion getExperimentalVersions(boolean forceDownload) throws IOException {
		return getVersions(options.experimentalVersionManifestUrl(), options.experimentalVersionManifestPath(), forceDownload);
	}

	private ManifestVersion getVersions(String url, Path cacheFile, boolean forceDownload) throws IOException {
		DownloadBuilder builder = download.apply(url);

		if (forceDownload) {
			builder = builder.forceDownload();
		} else {
			builder = builder.defaultCache();
		}

		final String versionManifest = builder.downloadString(cacheFile);
		return LoomGradlePlugin.GSON.fromJson(versionManifest, ManifestVersion.class);
	}

	private MinecraftVersionMeta readVersionMeta() throws IOException {
		final DownloadBuilder builder = download.apply(versionEntry.url);

		if (versionEntry.sha1 != null) {
			builder.sha1(versionEntry.sha1);
		} else {
			builder.defaultCache();
		}

		final String json = builder.downloadString(options.minecraftMetadataPath());
		return LoomGradlePlugin.GSON.fromJson(json, MinecraftVersionMeta.class);
	}

	public record Options(String minecraftVersion,
					String versionManifestUrl,
					String experimentalVersionManifestUrl,
					@Nullable String customManifestUrl,
					Path versionManifestPath,
					Path experimentalVersionManifestPath,
					Path minecraftMetadataPath) {
		public static Options create(String minecraftVersion, Project project, Path minecraftMetadataPath) {
			final LoomGradleExtension extension = LoomGradleExtension.get(project);
			final Path userCache = extension.getFiles().getUserCache().toPath();

			return new Options(
					minecraftVersion,
					MirrorUtil.getVersionManifests(project),
					MirrorUtil.getExperimentalVersions(project),
					extension.getCustomMinecraftManifest().getOrNull(),
					userCache.resolve("version_manifest.json"),
					userCache.resolve("experimental_version_manifest.json"),
					minecraftMetadataPath
			);
		}
	}

	@FunctionalInterface
	private interface ManifestVersionSupplier {
		ManifestVersion get() throws IOException;
	}
}
