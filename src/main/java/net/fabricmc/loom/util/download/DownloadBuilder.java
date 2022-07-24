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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@SuppressWarnings("UnusedReturnValue")
public class DownloadBuilder {
	private static final Duration ONE_DAY = Duration.ofDays(1);

	private final URI url;
	private String expectedHash = null;
	private boolean useEtag = true;
	private boolean forceDownload = false;
	private boolean offline = false;
	private Duration maxAge = Duration.ZERO;
	private DownloadProgressListener progressListener = DownloadProgressListener.NONE;

	private DownloadBuilder(URI url) {
		this.url = url;
	}

	static DownloadBuilder create(String url) throws URISyntaxException {
		return new DownloadBuilder(new URI(url));
	}

	public DownloadBuilder sha1(String sha1) {
		this.expectedHash = "sha1:" + sha1;
		return this;
	}

	public DownloadBuilder etag(boolean useEtag) {
		this.useEtag = useEtag;
		return this;
	}

	public DownloadBuilder forceDownload() {
		forceDownload = true;
		return this;
	}

	public DownloadBuilder offline() {
		offline = true;
		return this;
	}

	public DownloadBuilder maxAge(Duration duration) {
		this.maxAge = duration;
		return this;
	}

	public DownloadBuilder progress(DownloadProgressListener progressListener) {
		this.progressListener = progressListener;
		return this;
	}

	public DownloadBuilder defaultCache() {
		etag(true);
		return maxAge(ONE_DAY);
	}

	private Download build() {
		return new Download(this.url, this.expectedHash, this.useEtag, this.forceDownload, this.offline, maxAge, progressListener);
	}

	public CompletableFuture<Void> downloadPathAsync(Path path, DownloadExecutor executor) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				downloadPath(path);
			} catch (DownloadException e) {
				executor.downloadExceptions.add(e);
				throw new CompletionException(e);
			}

			return null;
		}, executor);
	}

	public void downloadPath(Path path) throws DownloadException {
		build().downloadPath(path);
	}

	public CompletableFuture<String> downloadStringAsync(DownloadExecutor executor) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				return downloadString();
			} catch (DownloadException e) {
				executor.downloadExceptions.add(e);
				throw new CompletionException(e);
			}
		}, executor);
	}

	public String downloadString() throws DownloadException {
		return build().downloadString();
	}

	public String downloadString(Path cache) throws DownloadException {
		downloadPath(cache);

		try {
			return Files.readString(cache, StandardCharsets.UTF_8);
		} catch (IOException e) {
			try {
				Files.delete(cache);
			} catch (IOException ex) {
				// Ignored
			}

			throw new DownloadException("Failed to download and read string", e);
		}
	}
}
