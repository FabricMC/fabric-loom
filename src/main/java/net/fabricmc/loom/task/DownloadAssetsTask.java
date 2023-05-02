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
import java.nio.file.Path;
import java.util.Objects;

import javax.inject.Inject;

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
import net.fabricmc.loom.util.download.DownloadExecutor;
import net.fabricmc.loom.util.download.GradleDownloadProgressListener;
import net.fabricmc.loom.util.gradle.ProgressGroup;

public abstract class DownloadAssetsTask extends AbstractLoomTask {
	@Input
	public abstract Property<String> getAssetsHash();

	@Input
	public abstract Property<Integer> getDownloadThreads();

	@Input
	public abstract Property<String> getMinecraftVersion();

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
		getDownloadThreads().convention(Math.min(Runtime.getRuntime().availableProcessors(), 10));
		getMinecraftVersion().set(versionInfo.id());
		getMinecraftVersion().finalizeValue();

		if (versionInfo.assets().equals("legacy")) {
			getLegacyResourcesDirectory().set(new File(assetsDir, "/legacy/" + versionInfo.id()));
		} else {
			// pre-1.6 resources
			RunConfigSettings client = Objects.requireNonNull(getExtension().getRunConfigs().findByName("client"), "Could not find client run config");
			getLegacyResourcesDirectory().set(new File(getProject().getProjectDir(), client.getRunDir() + "/resources"));
		}

		getAssetsHash().finalizeValue();
		getAssetsDirectory().finalizeValueOnRead();
		getLegacyResourcesDirectory().finalizeValueOnRead();
	}

	@TaskAction
	public void downloadAssets() throws IOException {
		final AssetIndex assetIndex = getAssetIndex();

		try (ProgressGroup progressGroup = new ProgressGroup(getProject(), "Download Assets");
				DownloadExecutor executor = new DownloadExecutor(getDownloadThreads().get())) {
			for (AssetIndex.Object object : assetIndex.getObjects()) {
				final String sha1 = object.hash();
				final String url = MirrorUtil.getResourcesBase(getProject()) + sha1.substring(0, 2) + "/" + sha1;

				getExtension()
						.download(url)
						.sha1(sha1)
						.progress(new GradleDownloadProgressListener(object.name(), progressGroup::createProgressLogger))
						.downloadPathAsync(getAssetsPath(object, assetIndex), executor);
			}
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

	private Path getAssetsPath(AssetIndex.Object object, AssetIndex index) {
		if (index.mapToResources() || index.virtual()) {
			return new File(getLegacyResourcesDirectory().get().getAsFile(), object.path()).toPath();
		}

		final String filename = "objects" + File.separator + object.hash().substring(0, 2) + File.separator + object.hash();
		return new File(getAssetsDirectory().get().getAsFile(), filename).toPath();
	}
}
