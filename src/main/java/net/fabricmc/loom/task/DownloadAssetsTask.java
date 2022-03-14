/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2021 FabricMC
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

package net.fabricmc.loom.task;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import javax.inject.Inject;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.configuration.ide.RunConfigSettings;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftProvider;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftVersionMeta;
import net.fabricmc.loom.configuration.providers.minecraft.assets.AssetIndex;
import net.fabricmc.loom.util.HashedDownloadUtil;
import net.fabricmc.loom.util.MirrorUtil;
import net.fabricmc.loom.util.gradle.ProgressLoggerHelper;

public abstract class DownloadAssetsTask extends AbstractLoomTask {
	@Input
	public abstract Property<String> getAssetsHash();

	@OutputDirectory
	public abstract RegularFileProperty getAssetsDirectory();

	@OutputDirectory
	public abstract RegularFileProperty getLegacyResourcesDirectory();

	@Inject
	public DownloadAssetsTask() {
		final MinecraftVersionMeta versionInfo = getExtension().getMinecraftProvider().getVersionInfo();
		final File assetsDir = new File(getExtension().getFiles().getUserCache(), "assets");

		getAssetsDirectory().set(assetsDir);
		getAssetsHash().set(versionInfo.assetIndex().sha1());

		if (versionInfo.assets().equals("legacy")) {
			getLegacyResourcesDirectory().set(new File(assetsDir, "/legacy/" + versionInfo.id()));
		} else {
			// pre-1.6 resources
			RunConfigSettings client = Objects.requireNonNull(getExtension().getRunConfigs().findByName("client"), "Could not find client run config");
			getLegacyResourcesDirectory().set(new File(getProject().getProjectDir(), client.getRunDir() + "/resources"));
		}

		getAssetsHash().finalizeValueOnRead();
		getAssetsDirectory().finalizeValueOnRead();
		getLegacyResourcesDirectory().finalizeValueOnRead();
	}

	@TaskAction
	public void downloadAssets() throws IOException {
		final Project project = this.getProject();
		final File assetsDirectory = getAssetsDirectory().get().getAsFile();
		final Deque<ProgressLoggerHelper> loggers = new ConcurrentLinkedDeque<>();
		final ExecutorService executor = Executors.newFixedThreadPool(Math.min(10, Math.max(Runtime.getRuntime().availableProcessors() / 2, 1)));
		final AssetIndex assetIndex = getAssetIndex();

		if (!assetsDirectory.exists()) {
			assetsDirectory.mkdirs();
		}

		if (assetIndex.mapToResources()) {
			getLegacyResourcesDirectory().get().getAsFile().mkdirs();
		}

		for (AssetIndex.Object object : assetIndex.getObjects()) {
			final String path = object.path();
			final String sha1 = object.hash();
			final File file = getAssetsFile(object, assetIndex);

			if (getProject().getGradle().getStartParameter().isOffline()) {
				if (!file.exists()) {
					throw new GradleException("Asset " + path + " not found at " + file.getAbsolutePath());
				}

				continue;
			}

			final Supplier<ProgressLoggerHelper> getOrCreateLogger = () -> {
				ProgressLoggerHelper logger = loggers.pollFirst();

				if (logger == null) {
					// No logger available, create a new one
					logger = ProgressLoggerHelper.getProgressFactory(project, DownloadAssetsTask.class.getName());
					logger.start("Downloading assets...", "assets");
				}

				return logger;
			};

			executor.execute(() -> {
				final ProgressLoggerHelper logger = getOrCreateLogger.get();

				try {
					HashedDownloadUtil.downloadIfInvalid(new URL(MirrorUtil.getResourcesBase(project) + sha1.substring(0, 2) + "/" + sha1), file, sha1, project.getLogger(), true, () -> {
						project.getLogger().debug("downloading asset " + object.name());
						logger.progress(String.format("%-30.30s", object.name()) + " - " + sha1);
					});
				} catch (IOException e) {
					throw new UncheckedIOException("Failed to download: " + object.name(), e);
				}

				// Give this logger back
				loggers.add(logger);
			});
		}

		// Wait for the assets to all download
		try {
			executor.shutdown();

			if (executor.awaitTermination(2, TimeUnit.HOURS)) {
				executor.shutdownNow();
			}
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		} finally {
			loggers.forEach(ProgressLoggerHelper::completed);
		}
	}

	private MinecraftVersionMeta.AssetIndex getAssetIndexMeta() {
		MinecraftVersionMeta versionInfo = getExtension().getMinecraftProvider().getVersionInfo();
		return versionInfo.assetIndex();
	}

	private AssetIndex getAssetIndex() throws IOException {
		final LoomGradleExtension extension = getExtension();
		final MinecraftProvider minecraftProvider = extension.getMinecraftProvider();

		MinecraftVersionMeta.AssetIndex assetIndex = getAssetIndexMeta();
		File assetsInfo = new File(getAssetsDirectory().get().getAsFile(), "indexes" + File.separator + assetIndex.fabricId(minecraftProvider.minecraftVersion()) + ".json");

		getProject().getLogger().info(":downloading asset index");

		if (getProject().getGradle().getStartParameter().isOffline()) {
			if (assetsInfo.exists()) {
				// We know it's outdated but can't do anything about it, oh well
				getProject().getLogger().warn("Asset index outdated");
			} else {
				// We don't know what assets we need, just that we don't have any
				throw new GradleException("Asset index not found at " + assetsInfo.getAbsolutePath());
			}
		} else {
			HashedDownloadUtil.downloadIfInvalid(new URL(assetIndex.url()), assetsInfo, assetIndex.sha1(), getProject().getLogger(), false);
		}

		try (FileReader fileReader = new FileReader(assetsInfo)) {
			return LoomGradlePlugin.OBJECT_MAPPER.readValue(fileReader, AssetIndex.class);
		}
	}

	private File getAssetsFile(AssetIndex.Object object, AssetIndex index) {
		if (index.mapToResources() || index.virtual()) {
			return new File(getLegacyResourcesDirectory().get().getAsFile(), object.path());
		}

		final String filename = "objects" + File.separator + object.hash().substring(0, 2) + File.separator + object.hash();
		return new File(getAssetsDirectory().get().getAsFile(), filename);
	}
}
