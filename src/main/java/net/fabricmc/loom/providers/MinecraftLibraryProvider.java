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

import com.google.gson.Gson;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.Checksum;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.MinecraftVersionInfo;
import net.fabricmc.loom.util.assets.AssetIndex;
import net.fabricmc.loom.util.assets.AssetObject;
import net.fabricmc.loom.util.progress.ProgressLogger;
import org.apache.commons.io.FileUtils;
import org.gradle.api.Project;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;

public class MinecraftLibraryProvider {

	public File MINECRAFT_LIBS;
	public File MINECRAFT_NATIVES;

	private Collection<File> libs = new HashSet<>();

	public void provide(MinecraftProvider minecraftProvider, Project project) throws IOException {
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		MinecraftVersionInfo versionInfo = minecraftProvider.versionInfo;

		initFiles(project, minecraftProvider);

		for (MinecraftVersionInfo.Library library : versionInfo.libraries) {
			if (library.allowed() && library.getFile(MINECRAFT_LIBS) != null) {
				// TODO: Add custom library locations

				// By default, they are all available on all sides
				/* boolean isClientOnly = false;
				if (library.name.contains("java3d") || library.name.contains("paulscode") || library.name.contains("lwjgl") || library.name.contains("twitch") || library.name.contains("jinput") || library.name.contains("text2speech") || library.name.contains("objc")) {
					isClientOnly = true;
				} */

				project.getDependencies().add(Constants.MINECRAFT_DEPENDENCIES, project.getDependencies().module(library.getArtifactName()));
			}
		}

		MinecraftVersionInfo.AssetIndex assetIndex = versionInfo.assetIndex;

		// get existing cache files
		File assets = new File(extension.getUserCache(), "assets");
		if (!assets.exists()) {
			assets.mkdirs();
		}

		File assetsInfo = new File(assets, "indexes" + File.separator + assetIndex.getFabricId(minecraftProvider.minecraftVersion) + ".json");
		if (!assetsInfo.exists() || !Checksum.equals(assetsInfo, assetIndex.sha1)) {
			project.getLogger().lifecycle(":downloading asset index");
			FileUtils.copyURLToFile(new URL(assetIndex.url), assetsInfo);
		}

		ProgressLogger progressLogger = ProgressLogger.getProgressFactory(project, getClass().getName());
		progressLogger.start("Downloading assets...", "assets");
		FileReader fileReader = new FileReader(assetsInfo);
		AssetIndex index = new Gson().fromJson(fileReader, AssetIndex.class);
		fileReader.close();
		Map<String, AssetObject> parent = index.getFileMap();
		final int totalSize = parent.size();
		int position = 0;
		project.getLogger().lifecycle(":downloading assets...");
		for (Map.Entry<String, AssetObject> entry : parent.entrySet()) {
			AssetObject object = entry.getValue();
			String sha1 = object.getHash();
			String filename = "objects" + File.separator + sha1.substring(0, 2) + File.separator + sha1;
			File file = new File(assets, filename);

			if (!file.exists() || !Checksum.equals(file, sha1)) {
				project.getLogger().debug(":downloading asset " + entry.getKey());
				FileUtils.copyURLToFile(new URL(Constants.RESOURCES_BASE + sha1.substring(0, 2) + "/" + sha1), file);
			}
			String assetName = entry.getKey();
			int end = assetName.lastIndexOf("/") + 1;
			if (end > 0) {
				assetName = assetName.substring(end);
			}
			progressLogger.progress(assetName + " - " + position + "/" + totalSize + " (" + (int) ((position / (double) totalSize) * 100) + "%) assets downloaded");
			position++;
		}

		progressLogger.completed();
	}

	public Collection<File> getLibraries() {
		return libs;
	}

	private void initFiles(Project project, MinecraftProvider minecraftProvider) {
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		MINECRAFT_LIBS = new File(extension.getUserCache(), "libraries");
	}

}
