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

package net.fabricmc.loom.configuration.providers.minecraft.assets;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Stopwatch;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import me.tongfei.progressbar.DelegatingProgressBarConsumer;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import me.tongfei.progressbar.ProgressBarStyle;
import org.gradle.api.GradleException;
import org.gradle.api.Project;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.configuration.providers.MinecraftProvider;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftVersionInfo;
import net.fabricmc.loom.util.Checksum;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.DownloadUtil;

public class MinecraftAssetsProvider {
	public static void provide(MinecraftProvider minecraftProvider, Project project) throws IOException {
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		boolean offline = project.getGradle().getStartParameter().isOffline();

		MinecraftVersionInfo versionInfo = minecraftProvider.getVersionInfo();
		MinecraftVersionInfo.AssetIndex assetIndex = versionInfo.assetIndex;

		// get existing cache files
		File assets = new File(extension.getUserCache(), "assets");

		if (!assets.exists()) {
			assets.mkdirs();
		}

		File assetsInfo = new File(assets, "indexes" + File.separator + assetIndex.getFabricId(minecraftProvider.getMinecraftVersion()) + ".json");
		File checksumInfo = new File(assets, "checksum" + File.separator + minecraftProvider.getMinecraftVersion() + ".json");

		if (!assetsInfo.exists() || !Checksum.equals(assetsInfo, assetIndex.sha1)) {
			project.getLogger().lifecycle(":downloading asset index");

			if (offline) {
				if (assetsInfo.exists()) {
					//We know it's outdated but can't do anything about it, oh well
					project.getLogger().warn("Asset index outdated");
				} else {
					//We don't know what assets we need, just that we don't have any
					throw new GradleException("Asset index not found at " + assetsInfo.getAbsolutePath());
				}
			} else {
				DownloadUtil.downloadIfChanged(new URL(assetIndex.url), assetsInfo, project.getLogger());
			}
		}

		Gson gson = new Gson();
		Map<String, String> checksumInfos = new ConcurrentHashMap<>();

		if (checksumInfo.exists()) {
			try (FileReader reader = new FileReader(checksumInfo)) {
				checksumInfos.putAll(gson.fromJson(reader, new TypeToken<Map<String, String>>() {
				}.getType()));
			}
		}

		ExecutorService executor = Executors.newFixedThreadPool(Math.min(16, Math.max(Runtime.getRuntime().availableProcessors() * 2, 1)));
		int toDownload = 0;

		AssetIndex index;

		try (FileReader fileReader = new FileReader(assetsInfo)) {
			index = gson.fromJson(fileReader, AssetIndex.class);
		}

		Stopwatch stopwatch = Stopwatch.createStarted();

		Map<String, AssetObject> parent = index.getFileMap();

		ProgressBar[] progressBar = {null};

		try {
			for (Map.Entry<String, AssetObject> entry : parent.entrySet()) {
				AssetObject object = entry.getValue();
				String sha1 = object.getHash();
				String filename = "objects" + File.separator + sha1.substring(0, 2) + File.separator + sha1;
				File file = new File(assets, filename);

				String localFileChecksum = !file.exists() ? null : checksumInfos.computeIfAbsent(entry.getKey(), path -> {
					try {
						return Files.asByteSource(file).hash(Hashing.sha1()).toString();
					} catch (IOException e) {
						e.printStackTrace();
						return null;
					}
				});

				if (LoomGradlePlugin.refreshDeps || localFileChecksum == null || !localFileChecksum.equals(sha1)) {
					if (offline) {
						if (file.exists()) {
							project.getLogger().warn("Outdated asset " + entry.getKey());
						} else {
							throw new GradleException("Asset " + entry.getKey() + " not found at " + file.getAbsolutePath());
						}
					} else {
						toDownload++;

						if (progressBar[0] == null) {
							progressBar[0] = new ProgressBarBuilder()
									.setConsumer(new DelegatingProgressBarConsumer(project.getLogger()::lifecycle))
									.setInitialMax(toDownload)
									.setUpdateIntervalMillis(2000)
									.setTaskName(":downloading assets")
									.setStyle(ProgressBarStyle.ASCII)
									.showSpeed()
									.build();
						}

						progressBar[0].maxHint(toDownload);

						executor.execute(() -> {
							String assetName = entry.getKey();
							int end = assetName.lastIndexOf("/") + 1;

							if (end > 0) {
								assetName = assetName.substring(end);
							}

							project.getLogger().debug(":downloading asset " + assetName);

							try {
								DownloadUtil.downloadIfChanged(new URL(Constants.RESOURCES_BASE + sha1.substring(0, 2) + "/" + sha1), file, project.getLogger(), true);
							} catch (IOException e) {
								throw new RuntimeException("Failed to download: " + assetName, e);
							}

							if (localFileChecksum == null) {
								checksumInfos.put(entry.getKey(), sha1);
							}

							synchronized (progressBar[0]) {
								progressBar[0].step();
							}
						});
					}
				}
			}

			project.getLogger().info("Took " + stopwatch.stop() + " to iterate " + parent.size() + " asset index.");

			if (toDownload > 0) {
				project.getLogger().lifecycle(":downloading " + toDownload + " asset" + (toDownload == 1 ? "" : "s") + "...");
			}

			checksumInfo.getParentFile().mkdirs();

			try (FileWriter writer = new FileWriter(checksumInfo)) {
				gson.toJson(checksumInfos, writer);
			}

			//Wait for the assets to all download
			executor.shutdown();

			try {
				if (executor.awaitTermination(2, TimeUnit.HOURS)) {
					executor.shutdownNow();
				}
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		} finally {
			if (progressBar[0] != null) {
				progressBar[0].close();
			}
		}
	}
}
