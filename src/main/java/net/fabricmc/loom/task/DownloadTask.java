/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 FabricMC
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

import java.net.URISyntaxException;
import java.time.Duration;

import javax.inject.Inject;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;
import org.jetbrains.annotations.ApiStatus;

import net.fabricmc.loom.util.ExceptionUtil;
import net.fabricmc.loom.util.download.Download;
import net.fabricmc.loom.util.download.DownloadBuilder;
import net.fabricmc.loom.util.download.DownloadException;

/**
 * A general purpose task for downloading files from a URL, using the loom {@link Download} utility.
 */
@DisableCachingByDefault
public abstract class DownloadTask extends DefaultTask {
	/**
	 * The URL to download the file from.
	 */
	@Input
	public abstract Property<String> getUrl();

	/**
	 * The expected SHA-1 hash of the downloaded file.
	 */
	@Optional
	@Input
	public abstract Property<String> getSha1();

	/**
	 * The maximum age of the downloaded file in days. When not provided the downloaded file will never be considered stale.
	 */
	@Optional
	@Input
	public abstract Property<Duration> getMaxAge();

	/**
	 * The file to download to.
	 */
	@OutputFile
	public abstract RegularFileProperty getOutput();

	// Internal stuff:

	@ApiStatus.Internal
	@Input
	protected abstract Property<Boolean> getIsOffline();

	@Inject
	protected abstract WorkerExecutor getWorkerExecutor();

	@Inject
	public DownloadTask() {
		getIsOffline().set(getProject().getGradle().getStartParameter().isOffline());
	}

	@TaskAction
	public void run() {
		final WorkQueue workQueue = getWorkerExecutor().noIsolation();

		workQueue.submit(DownloadAction.class, params -> {
			params.getUrl().set(getUrl());
			params.getSha1().set(getSha1());
			params.getMaxAge().set(getMaxAge());
			params.getOutputFile().set(getOutput());
			params.getIsOffline().set(getIsOffline());
		});
	}

	public interface DownloadWorkParameters extends WorkParameters {
		Property<String> getUrl();
		Property<String> getSha1();
		Property<Duration> getMaxAge();
		RegularFileProperty getOutputFile();
		Property<Boolean> getIsOffline();
	}

	public abstract static class DownloadAction implements WorkAction<DownloadWorkParameters> {
		@Override
		public void execute() {
			DownloadBuilder builder;

			try {
				builder = Download.create(getParameters().getUrl().get()).defaultCache();
			} catch (URISyntaxException e) {
				throw ExceptionUtil.createDescriptiveWrapper(RuntimeException::new, "Invalid URL", e);
			}

			if (getParameters().getMaxAge().isPresent()) {
				builder.maxAge(getParameters().getMaxAge().get());
			}

			if (getParameters().getSha1().isPresent()) {
				builder.sha1(getParameters().getSha1().get());
			}

			if (getParameters().getIsOffline().get()) {
				builder.offline();
			}

			try {
				builder.downloadPath(getParameters().getOutputFile().get().getAsFile().toPath());
			} catch (DownloadException e) {
				throw ExceptionUtil.createDescriptiveWrapper(RuntimeException::new, "Failed to download file", e);
			}
		}
	}
}
