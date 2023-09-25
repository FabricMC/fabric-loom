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
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;

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
	private int maxRetries = 3;
	private boolean allowInsecureProtocol = false;
	private HttpClient.Version httpVersion = HttpClient.Version.HTTP_2;

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

	public DownloadBuilder maxRetries(int maxRetries) {
		this.maxRetries = maxRetries;
		return this;
	}

	public DownloadBuilder defaultCache() {
		etag(true);
		return maxAge(ONE_DAY);
	}

	public DownloadBuilder allowInsecureProtocol() {
		this.allowInsecureProtocol = true;
		return this;
	}

	public DownloadBuilder httpVersion(HttpClient.Version httpVersion) {
		this.httpVersion = httpVersion;
		return this;
	}

	private Download build(int downloadAttempt) {
		if (!allowInsecureProtocol && !isSecureUrl(url)) {
			throw new IllegalArgumentException("Cannot create download for url (%s) with insecure protocol".formatted(url.toString()));
		}

		return new Download(this.url, this.expectedHash, this.useEtag, this.forceDownload, this.offline, maxAge, progressListener, httpVersion, downloadAttempt);
	}

	public void downloadPathAsync(Path path, DownloadExecutor executor) {
		executor.runAsync(() -> downloadPath(path));
	}

	public void downloadPath(Path path) throws DownloadException {
		withRetries((download) -> {
			download.downloadPath(path);
			return null;
		});
	}

	public String downloadString() throws DownloadException {
		return withRetries(Download::downloadString);
	}

	public String downloadString(Path cache) throws DownloadException {
		return withRetries((download) -> {
			download.downloadPath(cache);

			try {
				return Files.readString(cache, StandardCharsets.UTF_8);
			} catch (IOException e) {
				try {
					Files.deleteIfExists(cache);
				} catch (IOException ex) {
					// Ignored
				}

				throw new DownloadException("Failed to download and read string", e);
			}
		});
	}

	private <T> T withRetries(DownloadFunction<T> supplier) throws DownloadException {
		for (int i = 1; i <= maxRetries; i++) {
			try {
				if (i == maxRetries) {
					// Last ditch attempt, try over HTTP 1.1
					httpVersion(HttpClient.Version.HTTP_1_1);
				}

				return supplier.get(build(i));
			} catch (DownloadException e) {
				if (e.getStatusCode() == 404) {
					// Don't retry on 404's
					throw e;
				}

				if (i == maxRetries) {
					throw new DownloadException(String.format(Locale.ENGLISH, "Failed download after %d attempts", maxRetries), e);
				}
			}
		}

		throw new IllegalStateException();
	}

	// See comment on org.gradle.util.internal.GUtil.isSecureUrl
	private static boolean isSecureUrl(URI url) {
		if ("127.0.0.1".equals(url.getHost())) {
			return true;
		}

		final String scheme = url.getScheme();
		return !"http".equalsIgnoreCase(scheme);
	}

	@FunctionalInterface
	private interface DownloadFunction<T> {
		T get(Download download) throws DownloadException;
	}
}
