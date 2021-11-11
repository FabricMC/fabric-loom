/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2018-2021 FabricMC
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

package net.fabricmc.loom.configuration.providers;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.google.common.io.Files;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.configuration.DependencyProvider;
import net.fabricmc.loom.configuration.providers.minecraft.ManifestVersion;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftLibraryProvider;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftVersionMeta;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.MirrorUtil;
import net.fabricmc.loom.util.DownloadUtil;
import net.fabricmc.loom.util.HashedDownloadUtil;
import net.fabricmc.stitch.merge.JarMerger;

public class MinecraftProviderImpl extends DependencyProvider implements MinecraftProvider {
	private String minecraftVersion;

	private MinecraftVersionMeta versionInfo;
	private MinecraftLibraryProvider libraryProvider;

	private File workingDir;
	private File minecraftJson;
	private File minecraftClientJar;
	// Note this will be the boostrap jar starting with 21w39a
	private File minecraftServerJar;
	// The extracted server jar from the boostrap, only exists in >=21w39a
	private File minecraftExtractedServerJar;
	private File minecraftMergedJar;
	private File versionManifestJson;
	private File experimentalVersionsJson;

	public MinecraftProviderImpl(Project project) {
		super(project);
	}

	@Override
	public void provide(DependencyInfo dependency, Consumer<Runnable> postPopulationScheduler) throws Exception {
		minecraftVersion = dependency.getDependency().getVersion();

		boolean offline = getProject().getGradle().getStartParameter().isOffline();

		initFiles();

		downloadMcJson(offline);

		try (FileReader reader = new FileReader(minecraftJson)) {
			versionInfo = LoomGradlePlugin.OBJECT_MAPPER.readValue(reader, MinecraftVersionMeta.class);
		}

		if (offline) {
			if (minecraftClientJar.exists() && minecraftServerJar.exists()) {
				getProject().getLogger().debug("Found client and server jars, presuming up-to-date");
			} else if (minecraftMergedJar.exists()) {
				//Strictly we don't need the split jars if the merged one exists, let's try go on
				getProject().getLogger().warn("Missing game jar but merged jar present, things might end badly");
			} else {
				throw new GradleException("Missing jar(s); Client: " + minecraftClientJar.exists() + ", Server: " + minecraftServerJar.exists());
			}
		} else {
			downloadJars(getProject().getLogger());
		}

		libraryProvider = new MinecraftLibraryProvider();
		libraryProvider.provide(this, getProject());

		if (!minecraftMergedJar.exists() || isRefreshDeps()) {
			try {
				mergeJars(getProject().getLogger());
			} catch (Throwable e) {
				HashedDownloadUtil.delete(minecraftClientJar);
				HashedDownloadUtil.delete(minecraftServerJar);
				minecraftMergedJar.delete();

				getProject().getLogger().error("Could not merge JARs! Deleting source JARs - please re-run the command and move on.", e);
				throw e;
			}
		}
	}

	private void initFiles() {
		workingDir = new File(getDirectories().getUserCache(), minecraftVersion);
		workingDir.mkdirs();
		minecraftJson = file("minecraft-info.json");
		minecraftClientJar = file("minecraft-client.jar");
		minecraftServerJar = file("minecraft-server.jar");
		minecraftExtractedServerJar = file("minecraft-extracted_server.jar");
		minecraftMergedJar = file("minecraft-merged.jar");
		versionManifestJson = new File(getDirectories().getUserCache(), "version_manifest.json");
		experimentalVersionsJson = new File(getDirectories().getUserCache(), "experimental_version_manifest.json");
	}

	private void downloadMcJson(boolean offline) throws IOException {
		if (getExtension().getShareRemapCaches().get() && !getExtension().isRootProject() && versionManifestJson.exists() && !isRefreshDeps()) {
			return;
		}

		if (!offline && !isRefreshDeps() && hasRecentValidManifest()) {
			// We have a recent valid manifest file, so do nothing
		} else if (offline) {
			if (versionManifestJson.exists()) {
				// If there is the manifests already we'll presume that's good enough
				getProject().getLogger().debug("Found version manifests, presuming up-to-date");
			} else {
				// If we don't have the manifests then there's nothing more we can do
				throw new GradleException("Version manifests not found at " + versionManifestJson.getAbsolutePath());
			}
		} else {
			getProject().getLogger().debug("Downloading version manifests");
			DownloadUtil.downloadIfChanged(new URL(MirrorUtil.getVersionManifests(getProject())), versionManifestJson, getProject().getLogger());
		}

		String versionManifest = Files.asCharSource(versionManifestJson, StandardCharsets.UTF_8).read();
		ManifestVersion mcManifest = LoomGradlePlugin.OBJECT_MAPPER.readValue(versionManifest, ManifestVersion.class);

		Optional<ManifestVersion.Versions> optionalVersion = Optional.empty();

		if (getExtension().getCustomMinecraftManifest().isPresent()) {
			ManifestVersion.Versions customVersion = new ManifestVersion.Versions();
			customVersion.id = minecraftVersion;
			customVersion.url = getExtension().getCustomMinecraftManifest().get();
			optionalVersion = Optional.of(customVersion);
			getProject().getLogger().lifecycle("Using custom minecraft manifest");
		}

		if (optionalVersion.isEmpty()) {
			optionalVersion = mcManifest.versions().stream().filter(versions -> versions.id.equalsIgnoreCase(minecraftVersion)).findFirst();

			if (optionalVersion.isEmpty()) {
				optionalVersion = findExperimentalVersion(offline);
			}
		}

		if (optionalVersion.isPresent()) {
			if (offline) {
				if (minecraftJson.exists()) {
					//If there is the manifest already we'll presume that's good enough
					getProject().getLogger().debug("Found Minecraft {} manifest, presuming up-to-date", minecraftVersion);
				} else {
					//If we don't have the manifests then there's nothing more we can do
					throw new GradleException("Minecraft " + minecraftVersion + " manifest not found at " + minecraftJson.getAbsolutePath());
				}
			} else {
				getProject().getLogger().debug("Downloading Minecraft {} manifest", minecraftVersion);

				ManifestVersion.Versions version = optionalVersion.get();
				String url = version.url;

				if (version.sha1 != null) {
					HashedDownloadUtil.downloadIfInvalid(new URL(url), minecraftJson, version.sha1, getProject().getLogger(), true);
				} else {
					// Use the etag if no hash found from url
					DownloadUtil.downloadIfChanged(new URL(url), minecraftJson, getProject().getLogger());
				}
			}
		} else {
			throw new RuntimeException("Failed to find minecraft version: " + minecraftVersion);
		}
	}

	// This attempts to find the version from fabric's own fallback version manifest json.
	private Optional<ManifestVersion.Versions> findExperimentalVersion(boolean offline) throws IOException {
		if (offline) {
			if (!experimentalVersionsJson.exists()) {
				getProject().getLogger().warn("Skipping download of experimental versions jsons due to being offline.");
				return Optional.empty();
			}
		} else {
			DownloadUtil.downloadIfChanged(new URL(MirrorUtil.getExperimentalVersions(getProject())), experimentalVersionsJson, getProject().getLogger());
		}

		String expVersionManifest = Files.asCharSource(experimentalVersionsJson, StandardCharsets.UTF_8).read();
		ManifestVersion expManifest = LoomGradlePlugin.OBJECT_MAPPER.readValue(expVersionManifest, ManifestVersion.class);

		Optional<ManifestVersion.Versions> result = expManifest.versions().stream().filter(versions -> versions.id.equalsIgnoreCase(minecraftVersion)).findFirst();

		if (result.isPresent()) {
			getProject().getLogger().lifecycle("Using fallback experimental version {}", minecraftVersion);
		}

		return result;
	}

	private boolean hasRecentValidManifest() throws IOException {
		if (getExtension().getCustomMinecraftManifest().isPresent()) {
			return false;
		}

		if (!versionManifestJson.exists() || !minecraftJson.exists()) {
			return false;
		}

		if (versionManifestJson.lastModified() > System.currentTimeMillis() - 24 * 3_600_000) {
			// Version manifest hasn't been modified in 24 hours, time to get a new one.
			return false;
		}

		ManifestVersion manifest = LoomGradlePlugin.OBJECT_MAPPER.readValue(Files.asCharSource(versionManifestJson, StandardCharsets.UTF_8).read(), ManifestVersion.class);
		Optional<ManifestVersion.Versions> version = manifest.versions().stream().filter(versions -> versions.id.equalsIgnoreCase(minecraftVersion)).findFirst();

		// fail if the expected mc version was not found, will download the file again.
		return version.isPresent();
	}

	private void downloadJars(Logger logger) throws IOException {
		if (getExtension().getShareRemapCaches().get() && !getExtension().isRootProject() && minecraftClientJar.exists() && minecraftServerJar.exists() && !isRefreshDeps()) {
			return;
		}

		MinecraftVersionMeta.Download client = versionInfo.download("client");
		MinecraftVersionMeta.Download server = versionInfo.download("server");

		HashedDownloadUtil.downloadIfInvalid(new URL(client.url()), minecraftClientJar, client.sha1(), logger, false);
		HashedDownloadUtil.downloadIfInvalid(new URL(server.url()), minecraftServerJar, server.sha1(), logger, false);
	}

	private void mergeJars(Logger logger) throws IOException {
		logger.info(":merging jars");

		try (JarMerger jarMerger = new JarMerger(minecraftClientJar, getServerJarToMerge(logger), minecraftMergedJar)) {
			jarMerger.enableSyntheticParamsOffset();
			jarMerger.merge();
		}
	}

	private File getServerJarToMerge(Logger logger) throws IOException {
		try (ZipFile zipFile = new ZipFile(minecraftServerJar)) {
			ZipEntry versionsListEntry = zipFile.getEntry("META-INF/versions.list");

			if (versionsListEntry == null) {
				// Legacy pre 21w38a jar
				return minecraftServerJar;
			}

			logger.info(":Extracting server jar from bootstrap");

			String versionsList;

			try (InputStream is = zipFile.getInputStream(versionsListEntry)) {
				versionsList = new String(is.readAllBytes(), StandardCharsets.UTF_8);
			}

			String jarPath = null;
			String[] versions = versionsList.split("\n");

			if (versions.length != 1) {
				throw new UnsupportedOperationException("Expected only 1 version in META-INF/versions.list, but got %d".formatted(versions.length));
			}

			for (String version : versions) {
				if (version.isBlank()) continue;

				String[] split = version.split("\t");

				if (split.length != 3) continue;

				final String hash = split[0];
				final String id = split[1];
				final String path = split[2];

				// Take the first (only) version we find.
				jarPath = path;
				break;
			}

			Objects.requireNonNull(jarPath, "Could not find minecraft server jar for " + minecraftVersion());
			ZipEntry serverJarEntry = zipFile.getEntry("META-INF/versions/" + jarPath);
			Objects.requireNonNull(serverJarEntry, "Could not find server jar in boostrap@ " + jarPath);

			try (InputStream is = zipFile.getInputStream(serverJarEntry)) {
				java.nio.file.Files.copy(is, minecraftExtractedServerJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
			}

			return minecraftExtractedServerJar;
		}
	}

	public File getMergedJar() {
		return minecraftMergedJar;
	}

	@Override
	public File workingDir() {
		return workingDir;
	}

	@Override
	public boolean hasCustomNatives() {
		return getProject().getProperties().get("fabric.loom.natives.dir") != null;
	}

	@Override
	public File nativesDir() {
		if (hasCustomNatives()) {
			return new File((String) getProject().property("fabric.loom.natives.dir"));
		}

		return dir("natives");
	}

	@Override
	public File dir(String path) {
		File dir = file(path);
		dir.mkdirs();
		return dir;
	}

	@Override
	public File file(String path) {
		return new File(workingDir(), path);
	}

	@Override
	public String minecraftVersion() {
		return minecraftVersion;
	}

	@Override
	public MinecraftVersionMeta getVersionInfo() {
		return versionInfo;
	}

	public MinecraftLibraryProvider getLibraryProvider() {
		return libraryProvider;
	}

	@Override
	public String getTargetConfig() {
		return Constants.Configurations.MINECRAFT;
	}
}
