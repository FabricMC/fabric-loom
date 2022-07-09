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
import java.io.IOException;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
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

			// TODO look at this again, its a bit of a mess.
			CompletableFuture.supplyAsync(() -> {
				final ProgressLoggerHelper logger = getOrCreateLogger.get();
				project.getLogger().debug("downloading asset " + object.name());
				// TODO use the download util progress logger.
				logger.progress(String.format("%-30.30s", object.name()) + " - " + sha1);
				return logger;
			}, executor).thenCompose(logger -> {
				final String url = MirrorUtil.getResourcesBase(project) + sha1.substring(0, 2) + "/" + sha1;
				return getExtension()
						.download(url)
						.executor(executor)
						.downloadPathAsync(file.toPath())
						.thenApply(unused -> logger);
			}).thenApply((Function<ProgressLoggerHelper, Void>) logger -> {
				// Give this logger back
				loggers.add(logger);
				return null;
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
		final MinecraftVersionMeta.AssetIndex assetIndex = getAssetIndexMeta();
		final File indexFile = new File(getAssetsDirectory().get().getAsFile(), "indexes" + File.separator + assetIndex.fabricId(minecraftProvider.minecraftVersion()) + ".json");

		final String json = extension.download(assetIndex.url())
				.sha1(assetIndex.sha1())
				.downloadString(indexFile.toPath());

		return LoomGradlePlugin.OBJECT_MAPPER.readValue(json, AssetIndex.class);
	}

	private File getAssetsFile(AssetIndex.Object object, AssetIndex index) {
		if (index.mapToResources() || index.virtual()) {
			return new File(getLegacyResourcesDirectory().get().getAsFile(), object.path());
		}

		final String filename = "objects" + File.separator + object.hash().substring(0, 2) + File.separator + object.hash();
		return new File(getAssetsDirectory().get().getAsFile(), filename);
	}
}
