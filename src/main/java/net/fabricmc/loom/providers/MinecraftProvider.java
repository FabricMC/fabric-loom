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

package net.fabricmc.loom.providers;

import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.*;

import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Consumer;

public class MinecraftProvider extends DependencyProvider {

	public String minecraftVersion;

	public MinecraftVersionInfo versionInfo;
	public MinecraftLibraryProvider libraryProvider;
	public MinecraftJarProvider jarProvider;

	File MINECRAFT_JSON;
	File MINECRAFT_CLIENT_JAR;
	File MINECRAFT_SERVER_JAR;
	File MINECRAFT_MERGED_JAR;

	Gson gson = new Gson();

	@Override
	public void provide(DependencyInfo dependency, Project project, LoomGradleExtension extension, Consumer<Runnable> postPopulationScheduler) throws Exception {
		minecraftVersion = dependency.getDependency().getVersion();

		initFiles(project);

		downloadMcJson(project);
		FileReader reader = new FileReader(MINECRAFT_JSON);
		versionInfo = gson.fromJson(reader, MinecraftVersionInfo.class);
		reader.close();

		// Add Loom as an annotation processor
        addDependency(project.files(this.getClass().getProtectionDomain().getCodeSource().getLocation()), project, "compileOnly");

		downloadJars(project.getLogger());

		libraryProvider = new MinecraftLibraryProvider();
		libraryProvider.provide(this, project);
		jarProvider = new MinecraftJarProvider(project, this);
	}

	private void initFiles(Project project) {
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		MINECRAFT_JSON = new File(extension.getUserCache(), "minecraft-" + minecraftVersion + "-info.json");
		MINECRAFT_CLIENT_JAR = new File(extension.getUserCache(), "minecraft-" + minecraftVersion + "-client.jar");
		MINECRAFT_SERVER_JAR = new File(extension.getUserCache(), "minecraft-" + minecraftVersion + "-server.jar");
		MINECRAFT_MERGED_JAR = new File(extension.getUserCache(), "minecraft-" + minecraftVersion + "-merged.jar");

	}

	private void downloadMcJson(Project project) throws IOException {
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		File manifests = new File(extension.getUserCache(), "version_manifest.json");

		project.getLogger().debug("Downloading version manifests");
		DownloadUtil.downloadIfChanged(new URL("https://launchermeta.mojang.com/mc/game/version_manifest.json"), manifests, project.getLogger());
		String versionManifest = Files.asCharSource(manifests, StandardCharsets.UTF_8).read();
		ManifestVersion mcManifest = new GsonBuilder().create().fromJson(versionManifest, ManifestVersion.class);

		Optional<ManifestVersion.Versions> optionalVersion = mcManifest.versions.stream().filter(versions -> versions.id.equalsIgnoreCase(minecraftVersion)).findFirst();
		if (optionalVersion.isPresent()) {
			project.getLogger().debug("Downloading Minecraft {} manifest", minecraftVersion);
			DownloadUtil.downloadIfChanged(new URL(optionalVersion.get().url), MINECRAFT_JSON, project.getLogger());
		} else {
			throw new RuntimeException("Failed to find minecraft version: " + minecraftVersion);
		}

	}

	private void downloadJars(Logger logger) throws IOException {
		if (!MINECRAFT_CLIENT_JAR.exists() || !Checksum.equals(MINECRAFT_CLIENT_JAR, versionInfo.downloads.get("client").sha1)) {
			logger.debug("Downloading Minecraft {} client jar", minecraftVersion);
			DownloadUtil.downloadIfChanged(new URL(versionInfo.downloads.get("client").url), MINECRAFT_CLIENT_JAR, logger);
		}

		if (!MINECRAFT_SERVER_JAR.exists() || !Checksum.equals(MINECRAFT_SERVER_JAR, versionInfo.downloads.get("server").sha1)) {
			logger.debug("Downloading Minecraft {} server jar", minecraftVersion);
			DownloadUtil.downloadIfChanged(new URL(versionInfo.downloads.get("server").url), MINECRAFT_SERVER_JAR, logger);
		}
	}

	@Override
	public String getTargetConfig() {
		return Constants.MINECRAFT;
	}
}
