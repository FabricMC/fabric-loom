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

package net.fabricmc.loom.configuration.providers.minecraft;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.google.common.io.Files;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.configuration.DependencyInfo;
import net.fabricmc.loom.configuration.providers.BundleMetadata;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.DownloadUtil;
import net.fabricmc.loom.util.HashedDownloadUtil;
import net.fabricmc.loom.util.MirrorUtil;

public abstract class MinecraftProvider {
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
	@Nullable
	private BundleMetadata serverBundleMetadata;
	private File versionManifestJson;
	private File experimentalVersionsJson;

	private final Project project;

	public MinecraftProvider(Project project) {
		this.project = project;
	}

	public void provide() throws Exception {
		final DependencyInfo dependency = DependencyInfo.create(getProject(), Constants.Configurations.MINECRAFT);
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
			} else {
				throw new GradleException("Missing jar(s); Client: " + minecraftClientJar.exists() + ", Server: " + minecraftServerJar.exists());
			}
		} else {
			downloadJars(getProject().getLogger());
		}

		serverBundleMetadata = BundleMetadata.fromJar(minecraftServerJar.toPath());

		libraryProvider = new MinecraftLibraryProvider();
		libraryProvider.provide(this, getProject());
	}

	protected void initFiles() {
		workingDir = new File(getExtension().getFiles().getUserCache(), minecraftVersion);
		workingDir.mkdirs();
		minecraftJson = file("minecraft-info.json");
		minecraftClientJar = file("minecraft-client.jar");
		minecraftServerJar = file("minecraft-server.jar");
		minecraftExtractedServerJar = file("minecraft-extracted_server.jar");
		versionManifestJson = new File(getExtension().getFiles().getUserCache(), "version_manifest.json");
		experimentalVersionsJson = new File(getExtension().getFiles().getUserCache(), "experimental_version_manifest.json");
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

	protected final void extractBundledServerJar() throws IOException {
		Objects.requireNonNull(getServerBundleMetadata(), "Cannot bundled mc jar from none bundled server jar");

		getLogger().info(":Extracting server jar from bootstrap");

		if (getServerBundleMetadata().versions().size() != 1) {
			throw new UnsupportedOperationException("Expected only 1 version in META-INF/versions.list, but got %d".formatted(getServerBundleMetadata().versions().size()));
		}

		getServerBundleMetadata().versions().get(0).unpackEntry(minecraftServerJar.toPath(), getMinecraftExtractedServerJar().toPath());
	}

	public File workingDir() {
		return workingDir;
	}

	public File dir(String path) {
		File dir = file(path);
		dir.mkdirs();
		return dir;
	}

	public File file(String path) {
		return new File(workingDir(), path);
	}

	public Path path(String path) {
		return file(path).toPath();
	}

	public File getMinecraftClientJar() {
		return minecraftClientJar;
	}

	// May be null on older versions
	@Nullable
	public File getMinecraftExtractedServerJar() {
		return minecraftExtractedServerJar;
	}

	// This may be the server bundler jar on newer versions prob not what you want.
	public File getMinecraftServerJar() {
		return minecraftServerJar;
	}

	public String minecraftVersion() {
		return minecraftVersion;
	}

	public MinecraftVersionMeta getVersionInfo() {
		return versionInfo;
	}

	public MinecraftLibraryProvider getLibraryProvider() {
		return libraryProvider;
	}

	public String getTargetConfig() {
		return Constants.Configurations.MINECRAFT;
	}

	@Nullable
	public BundleMetadata getServerBundleMetadata() {
		return serverBundleMetadata;
	}

	protected Logger getLogger() {
		return getProject().getLogger();
	}

	public abstract List<Path> getMinecraftJars();

	protected Project getProject() {
		return project;
	}

	protected LoomGradleExtension getExtension() {
		return LoomGradleExtension.get(getProject());
	}

	protected boolean isRefreshDeps() {
		return LoomGradlePlugin.refreshDeps;
	}
}
