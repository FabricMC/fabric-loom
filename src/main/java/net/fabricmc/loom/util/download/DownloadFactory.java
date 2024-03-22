/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2024 FabricMC
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

package net.fabricmc.loom.util.download;

import java.net.URISyntaxException;

import javax.inject.Inject;

import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;

import net.fabricmc.loom.LoomGradleExtension;

/**
 * Can be used to create a {@link DownloadBuilder} with the correct settings for the project within a task.
 */
public abstract class DownloadFactory {
	@Input
	protected abstract Property<Boolean> getIsOffline();

	@Input
	protected abstract Property<Boolean> getIsManualRefreshDependencies();

	@Inject
	public abstract Project getProject();

	@Inject
	public DownloadFactory() {
		getIsOffline().set(getProject().getGradle().getStartParameter().isOffline());
		getIsManualRefreshDependencies().set(LoomGradleExtension.get(getProject()).refreshDeps());
	}

	// Matches the logic in LoomGradleExtensionImpl
	public DownloadBuilder download(String url) {
		DownloadBuilder builder;

		try {
			builder = Download.create(url);
		} catch (URISyntaxException e) {
			throw new RuntimeException("Failed to create downloader for: " + e);
		}

		if (getIsOffline().get()) {
			builder.offline();
		}

		if (getIsManualRefreshDependencies().get()) {
			builder.forceDownload();
		}

		return builder;
	}
}
