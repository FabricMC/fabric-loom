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
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import com.google.common.base.Preconditions;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.configuration.DependencyInfo;
import net.fabricmc.loom.configuration.providers.BundleMetadata;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.MirrorUtil;
import net.fabricmc.loom.util.download.DownloadBuilder;
import net.fabricmc.loom.util.download.DownloadExecutor;
import net.fabricmc.loom.util.download.GradleDownloadProgressListener;
import net.fabricmc.loom.util.gradle.ProgressGroup;

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

	protected boolean provideClient() {
		return true;
	}

	protected boolean provideServer() {
		return true;
	}

	public void provide() throws Exception {
		final DependencyInfo dependency = DependencyInfo.create(getProject(), Constants.Configurations.MINECRAFT);
		minecraftVersion = dependency.getDependency().getVersion();

		initFiles();

		downloadMcJson();

		try (FileReader reader = new FileReader(minecraftJson)) {
			versionInfo = LoomGradlePlugin.OBJECT_MAPPER.readValue(reader, MinecraftVersionMeta.class);
		}

		downloadJars();

		if (provideServer()) {
			serverBundleMetadata = BundleMetadata.fromJar(minecraftServerJar.toPath());
		}

		libraryProvider = new MinecraftLibraryProvider(this, project);
		libraryProvider.provide();
	}

	protected void initFiles() {
		workingDir = new File(getExtension().getFiles().getUserCache(), minecraftVersion);
		workingDir.mkdirs();
		minecraftJson = file("minecraft-info.json");
		versionManifestJson = new File(getExtension().getFiles().getUserCache(), "version_manifest.json");
		experimentalVersionsJson = new File(getExtension().getFiles().getUserCache(), "experimental_version_manifest.json");

		if (provideClient()) {
			minecraftClientJar = file("minecraft-client.jar");
		}

		if (provideServer()) {
			minecraftServerJar = file("minecraft-server.jar");
			minecraftExtractedServerJar = file("minecraft-extracted_server.jar");
		}
	}

	private void downloadMcJson() throws IOException {
		final String versionManifestUrl = MirrorUtil.getVersionManifests(getProject());
		final String versionManifest = getExtension().download(versionManifestUrl)
				.defaultCache()
				.downloadString(versionManifestJson.toPath());

		final ManifestVersion mcManifest = LoomGradlePlugin.OBJECT_MAPPER.readValue(versionManifest, ManifestVersion.class);
		ManifestVersion.Versions version = null;

		if (getExtension().getCustomMinecraftManifest().isPresent()) {
			ManifestVersion.Versions customVersion = new ManifestVersion.Versions();
			customVersion.id = minecraftVersion;
			customVersion.url = getExtension().getCustomMinecraftManifest().get();
			version = customVersion;
			getProject().getLogger().lifecycle("Using custom minecraft manifest");
		}

		if (version == null) {
			version = mcManifest.versions().stream()
					.filter(versions -> versions.id.equalsIgnoreCase(minecraftVersion))
					.findFirst().orElse(null);
		}

		if (version == null) {
			version = findExperimentalVersion();
		}

		if (version == null) {
			throw new RuntimeException("Failed to find minecraft version: " + minecraftVersion);
		}

		getProject().getLogger().debug("Downloading Minecraft {} manifest", minecraftVersion);
		final DownloadBuilder download = getExtension().download(version.url);

		if (version.sha1 != null) {
			download.sha1(version.sha1);
		} else {
			download.defaultCache();
		}

		download.downloadPath(minecraftJson.toPath());
	}

	// This attempts to find the version from fabric's own fallback version manifest json.
	private ManifestVersion.Versions findExperimentalVersion() throws IOException {
		final String expVersionManifest = getExtension().download(MirrorUtil.getExperimentalVersions(getProject()))
				.defaultCache()
				.downloadString(experimentalVersionsJson.toPath());

		final ManifestVersion expManifest = LoomGradlePlugin.OBJECT_MAPPER.readValue(expVersionManifest, ManifestVersion.class);
		final ManifestVersion.Versions result = expManifest.versions().stream()
				.filter(versions -> versions.id.equalsIgnoreCase(minecraftVersion))
				.findFirst()
				.orElse(null);

		if (result != null) {
			getProject().getLogger().lifecycle("Using fallback experimental version {}", minecraftVersion);
		}

		return result;
	}

	private void downloadJars() throws IOException {
		try (ProgressGroup progressGroup = new ProgressGroup(getProject(), "Download Minecraft jars");
				DownloadExecutor executor = new DownloadExecutor(2)) {
			if (provideClient()) {
				final MinecraftVersionMeta.Download client = versionInfo.download("client");
				getExtension().download(client.url())
						.sha1(client.sha1())
						.progress(new GradleDownloadProgressListener("Minecraft client", progressGroup::createProgressLogger))
						.downloadPathAsync(minecraftClientJar.toPath(), executor);
			}

			if (provideServer()) {
				final MinecraftVersionMeta.Download server = versionInfo.download("server");
				getExtension().download(server.url())
						.sha1(server.sha1())
						.progress(new GradleDownloadProgressListener("Minecraft server", progressGroup::createProgressLogger))
						.downloadPathAsync(minecraftServerJar.toPath(), executor);
			}
		}
	}

	protected final void extractBundledServerJar() throws IOException {
		Preconditions.checkArgument(provideServer(), "Not configured to provide server jar");
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
		Preconditions.checkArgument(provideClient(), "Not configured to provide client jar");
		return minecraftClientJar;
	}

	// May be null on older versions
	@Nullable
	public File getMinecraftExtractedServerJar() {
		Preconditions.checkArgument(provideServer(), "Not configured to provide server jar");
		return minecraftExtractedServerJar;
	}

	// This may be the server bundler jar on newer versions prob not what you want.
	public File getMinecraftServerJar() {
		Preconditions.checkArgument(provideServer(), "Not configured to provide server jar");
		return minecraftServerJar;
	}

	public String minecraftVersion() {
		return minecraftVersion;
	}

	public MinecraftVersionMeta getVersionInfo() {
		return versionInfo;
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
