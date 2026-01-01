/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022 FabricMC
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

import java.util.Objects;
import java.util.function.Function;

import org.gradle.internal.logging.progress.ProgressLogger;
import org.jspecify.annotations.Nullable;

public class GradleDownloadProgressListener implements DownloadProgressListener {
	private final String name;
	private final Function<String, ProgressLogger> progressLoggerFactory;

	private static final long REPORT_INTERVAL_MS = 500;

	@Nullable
	private ProgressLogger progressLogger;
	private long lastReportTime = 0;

	public GradleDownloadProgressListener(String name, Function<String, ProgressLogger> progressLoggerFactory) {
		this.name = name;
		this.progressLoggerFactory = progressLoggerFactory;
	}

	@Override
	public void onStart() {
		progressLogger = progressLoggerFactory.apply(this.name);
		lastReportTime = 0;
	}

	@Override
	public void onProgress(long bytesTransferred, long contentLength) {
		Objects.requireNonNull(progressLogger);

		long currentTime = System.currentTimeMillis();

		// Only update if at least 0.5 seconds have passed since last report, or if we're at 100%
		if (currentTime - lastReportTime >= REPORT_INTERVAL_MS || bytesTransferred >= contentLength) {
			progressLogger.progress("Downloading %s - %s / %s".formatted(name, humanBytes(bytesTransferred), humanBytes(contentLength)));
			lastReportTime = currentTime;
		}
	}

	@Override
	public void onEnd() {
		if (progressLogger != null) {
			progressLogger.completed();
			progressLogger = null;
		}
	}

	private static final long KB = 1024L;
	private static final long MB = KB * 1024L;
	private static final long GB = MB * 1024L;
	private static final double KB_DIVISOR = 1024;
	private static final double MB_DIVISOR = 1024 * 1024;
	private static final double GB_DIVISOR = 1024 * 1024 * 1024;

	private static String humanBytes(long bytes) {
		if (bytes < KB) {
			return bytes + " B";
		} else if (bytes < MB) {
			return (bytes / KB_DIVISOR) + " KB";
		} else if (bytes < GB) {
			return String.format("%.2f MB", bytes / MB_DIVISOR);
		} else {
			return String.format("%.2f GB", bytes / GB_DIVISOR);
		}
	}
}
