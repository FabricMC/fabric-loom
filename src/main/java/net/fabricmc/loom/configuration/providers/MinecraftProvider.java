/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016, 2017, 2018 FabricMC
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
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipError;

import com.google.common.io.Files;
import com.google.gson.GsonBuilder;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.configuration.DependencyProvider;
import net.fabricmc.loom.configuration.providers.minecraft.ManifestVersion;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftLibraryProvider;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftVersionInfo;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.DownloadUtil;
import net.fabricmc.loom.util.HashedDownloadUtil;
import net.fabricmc.stitch.merge.JarMerger;

public class MinecraftProvider extends DependencyProvider {
	private String minecraftVersion;

	private MinecraftVersionInfo versionInfo;
	private MinecraftLibraryProvider libraryProvider;

	private File minecraftJson;
	private File minecraftClientJar;
	private File minecraftServerJar;
	private File minecraftMergedJar;
	private File versionManifestJson;

	public MinecraftProvider(Project project) {
		super(project);
	}

	@Override
	public void provide(DependencyInfo dependency, Consumer<Runnable> postPopulationScheduler) throws Exception {
		minecraftVersion = dependency.getDependency().getVersion();

		boolean offline = getProject().getGradle().getStartParameter().isOffline();

		initFiles();

		downloadMcJson(offline);

		try (FileReader reader = new FileReader(minecraftJson)) {
			versionInfo = LoomGradlePlugin.GSON.fromJson(reader, MinecraftVersionInfo.class);
		}

		// Add Loom as an annotation processor
		addDependency(getProject().files(this.getClass().getProtectionDomain().getCodeSource().getLocation()), "compileOnly");

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
			} catch (ZipError e) {
				DownloadUtil.delete(minecraftClientJar);
				DownloadUtil.delete(minecraftServerJar);

				getProject().getLogger().error("Could not merge JARs! Deleting source JARs - please re-run the command and move on.", e);
				throw new RuntimeException();
			}
		}
	}

	private void initFiles() {
		minecraftJson = new File(getExtension().getUserCache(), "minecraft-" + minecraftVersion + "-info.json");
		minecraftClientJar = new File(getExtension().getUserCache(), "minecraft-" + minecraftVersion + "-client.jar");
		minecraftServerJar = new File(getExtension().getUserCache(), "minecraft-" + minecraftVersion + "-server.jar");
		minecraftMergedJar = new File(getExtension().getUserCache(), "minecraft-" + minecraftVersion + "-merged.jar");
		versionManifestJson = new File(getExtension().getUserCache(), "version_manifest.json");
	}

	private void downloadMcJson(boolean offline) throws IOException {
		if (getExtension().isShareCaches() && !getExtension().isRootProject() && versionManifestJson.exists() && !isRefreshDeps()) {
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
			DownloadUtil.downloadIfChanged(new URL(Constants.VERSION_MANIFESTS), versionManifestJson, getProject().getLogger());
		}

		String versionManifest = Files.asCharSource(versionManifestJson, StandardCharsets.UTF_8).read();
		ManifestVersion mcManifest = new GsonBuilder().create().fromJson(versionManifest, ManifestVersion.class);

		Optional<ManifestVersion.Versions> optionalVersion = Optional.empty();

		if (getExtension().customManifest != null) {
			ManifestVersion.Versions customVersion = new ManifestVersion.Versions();
			customVersion.id = minecraftVersion;
			customVersion.url = getExtension().customManifest;
			optionalVersion = Optional.of(customVersion);
			getProject().getLogger().lifecycle("Using custom minecraft manifest");
		}

		if (!optionalVersion.isPresent()) {
			optionalVersion = mcManifest.versions.stream().filter(versions -> versions.id.equalsIgnoreCase(minecraftVersion)).findFirst();
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

				String url = optionalVersion.get().url;
				// Find the sha1 of the json from the url, return true if it matches the local json
				Pattern sha1Pattern = Pattern.compile("\\b[0-9a-f]{5,40}\\b");
				Matcher matcher = sha1Pattern.matcher(url);

				if (matcher.find()) {
					HashedDownloadUtil.downloadIfInvalid(new URL(url), minecraftJson, matcher.group(), getProject().getLogger(), true);
				} else {
					// Use the etag if no hash found from url
					DownloadUtil.downloadIfChanged(new URL(url), minecraftJson, getProject().getLogger());
				}
			}
		} else {
			throw new RuntimeException("Failed to find minecraft version: " + minecraftVersion);
		}
	}

	private boolean hasRecentValidManifest() throws IOException {
		if (getExtension().customManifest != null) {
			return false;
		}

		if (!versionManifestJson.exists() || !minecraftJson.exists()) {
			return false;
		}

		if (versionManifestJson.lastModified() > System.currentTimeMillis() - 24 * 3_600_000) {
			// Version manifest hasn't been modified in 24 hours, time to get a new one.
			return false;
		}

		ManifestVersion manifest = new GsonBuilder().create().fromJson(Files.asCharSource(versionManifestJson, StandardCharsets.UTF_8).read(), ManifestVersion.class);
		Optional<ManifestVersion.Versions> version = manifest.versions.stream().filter(versions -> versions.id.equalsIgnoreCase(minecraftVersion)).findFirst();

		// fail if the expected mc version was not found, will download the file again.
		return version.isPresent();
	}

	private void downloadJars(Logger logger) throws IOException {
		if (getExtension().isShareCaches() && !getExtension().isRootProject() && minecraftClientJar.exists() && minecraftServerJar.exists() && !isRefreshDeps()) {
			return;
		}

		MinecraftVersionInfo.Downloads client = versionInfo.downloads.get("client");
		MinecraftVersionInfo.Downloads server = versionInfo.downloads.get("server");

		HashedDownloadUtil.downloadIfInvalid(new URL(client.url), minecraftClientJar, client.sha1, logger, false);
		HashedDownloadUtil.downloadIfInvalid(new URL(server.url), minecraftServerJar, server.sha1, logger, false);
	}

	private void mergeJars(Logger logger) throws IOException {
		logger.lifecycle(":merging jars");

		try (JarMerger jarMerger = new JarMerger(minecraftClientJar, minecraftServerJar, minecraftMergedJar)) {
			jarMerger.enableSyntheticParamsOffset();
			jarMerger.merge();
		}
	}

	public File getMergedJar() {
		return minecraftMergedJar;
	}

	public String getMinecraftVersion() {
		return minecraftVersion;
	}

	public MinecraftVersionInfo getVersionInfo() {
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
