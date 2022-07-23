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
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import com.github.mizosoft.methanol.Methanol;
import com.github.mizosoft.methanol.ProgressTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.util.Checksum;

// TODO better error handling
public class Download {
	private static final String E_TAG = "ETag";
	private static final Logger LOGGER = LoggerFactory.getLogger(Download.class);

	public static DownloadBuilder create(String url) throws URISyntaxException {
		return DownloadBuilder.create(url);
	}

	private final URI url;
	private final ExecutorService executor;
	private final String expectedHash;
	private final boolean useEtag;
	private final boolean forceDownload;
	private final boolean offline;
	private final Duration maxAge;
	private final DownloadProgressListener progressListener = DownloadProgressListener.NONE;

	Download(URI url, ExecutorService executor, String expectedHash, boolean useEtag, boolean forceDownload, boolean offline, Duration maxAge) {
		this.url = url;
		this.executor = executor;
		this.expectedHash = expectedHash;
		this.useEtag = useEtag;
		this.forceDownload = forceDownload;
		this.offline = offline;
		this.maxAge = maxAge;
	}

	private HttpClient getHttpClient() {
		if (offline) {
			throw error("Unable to download %s in offline mode", this.url);
		}

		return Methanol.newBuilder()
				.executor(executor)
				.followRedirects(HttpClient.Redirect.ALWAYS)
				.proxy(ProxySelector.getDefault())
				.autoAcceptEncoding(true)
				.build();
	}

	private HttpRequest getRequest() {
		return HttpRequest.newBuilder(url)
				.GET()
				.build();
	}

	private HttpRequest getETagRequest(String etag) {
		return HttpRequest.newBuilder(url)
				.GET()
				.header("If-None-Match", etag)
				.build();
	}

	private <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest httpRequest, HttpResponse.BodyHandler<T> bodyHandler) {
		final ProgressTracker tracker = ProgressTracker.create();
		final AtomicBoolean started = new AtomicBoolean(false);

		return getHttpClient().sendAsync(httpRequest, tracker.tracking(bodyHandler, progress -> {
			if (started.compareAndSet(false, true)) {
				progressListener.onStart(url.toString());
			}

			progressListener.onProgress(progress.value());

			if (progress.done()) {
				progressListener.onEnd(true);
			}
		}));
	}

	CompletableFuture<String> downloadString() {
		// No caching to be done.
		return sendAsync(getRequest(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
				.thenApply(response -> {
					final int statusCode = response.statusCode();

					if (statusCode < 200 || statusCode > 299) {
						throw error("HTTP request to (%s) returned unsuccessful status (%d)", url, statusCode);
					}

					return response.body();
				});
	}

	CompletableFuture<Void> downloadPath(Path output) {
		return CompletableFuture.supplyAsync(() -> requiresDownload(output), executor)
				.thenCompose(downloadRequired -> {
					if (!downloadRequired) {
						// Does not require download, we are done here.
						return CompletableFuture.completedFuture(null);
					}

					return doDownload(output);
				}).exceptionally(throwable -> {
					tryCleanup(output);

					throw error(throwable, "Failed to download (%s) to (%s)", url, output);
				});
	}

	private CompletableFuture<Void> doDownload(Path output) {
		Optional<String> eTag = Optional.empty();

		if (!forceDownload && useEtag && exists(output)) {
			eTag = readEtag(output);
		}

		try {
			Files.createDirectories(output.getParent());
			Files.deleteIfExists(output);
		} catch (IOException e) {
			throw error(e, "Failed to prepare path for download");
		}

		final HttpRequest httpRequest = eTag
				.map(this::getETagRequest)
				.orElseGet(this::getRequest);

		return sendAsync(httpRequest, HttpResponse.BodyHandlers.ofFile(output)).thenCompose(response -> {
			final int statusCode = response.statusCode();

			if (statusCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
				// Success, etag matched.
				return CompletableFuture.completedFuture(null);
			}

			if (statusCode < 200 || statusCode > 299) {
				throw error("HTTP request to (%s) returned unsuccessful status (%d)", url, statusCode);
			}

			if (useEtag) {
				final HttpHeaders headers = response.headers();
				headers.firstValue(E_TAG.toLowerCase(Locale.ROOT))
						.ifPresent(responseETag -> writeEtag(output, responseETag));
			}

			if (expectedHash != null) {
				// Ensure we downloaded the expected hash.
				if (!isHashValid(output)) {
					String downloadedHash;

					try {
						downloadedHash = Checksum.sha1Hex(output);
					} catch (IOException e) {
						downloadedHash = "unknown hash";
					}

					throw error("Failed to download (%s) with expected hash: %s got %s", url, expectedHash, downloadedHash);
				}
			}

			// We are done :)
			return CompletableFuture.completedFuture(null);
		});
	}

	private boolean requiresDownload(Path output) {
		if (forceDownload || !exists(output)) {
			// File does not exist, or we are forced to download again.
			return true;
		}

		if (offline) {
			// We know the file exists, nothing more we can do.
			return false;
		}

		if (expectedHash != null) {
			if (isHashValid(output)) {
				// Valid hash, no need to re-download
				return false;
			}

			if (System.getProperty("fabric.loom.test") != null) {
				// This should never happen in an ideal world.
				// It means that something has altered a file that should be cached.
				throw error("Download file (%s) may have been modified", output);
			}

			LOGGER.info("Found existing file ({}) to download with unexpected hash.", output);
		}

		//noinspection RedundantIfStatement
		if (!maxAge.equals(Duration.ZERO) && !isOutdated(output)) {
			return false;
		}

		// Default to re-downloading, may check the etag
		return true;
	}

	private boolean isHashValid(Path path) {
		int i = expectedHash.indexOf(':');
		String algorithm = expectedHash.substring(0, i);
		String hash = expectedHash.substring(i + 1);

		try {
			String computedHash = switch (algorithm) {
			case "sha1" -> Checksum.sha1Hex(path);
			default -> throw error("Unsupported hash algorithm (%s)", algorithm);
			};

			return computedHash.equalsIgnoreCase(hash);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private boolean isOutdated(Path path) {
		try {
			final FileTime lastModified = getLastModified(path);
			return lastModified.toInstant().plus(maxAge)
					.isBefore(Instant.now());
		} catch (IOException e) {
			throw error(e, "Failed to check if (%s) is outdated", path);
		}
	}

	private Optional<String> readEtag(Path output) {
		try {
			return readAttribute(output, E_TAG);
		} catch (IOException e) {
			return Optional.empty();
		}
	}

	private void writeEtag(Path output, String eTag) {
		try {
			writeAttribute(output, E_TAG, eTag);
		} catch (IOException e) {
			throw error(e, "Failed to write etag to (%s)", output);
		}
	}

	private void tryCleanup(Path output) {
		try {
			Files.deleteIfExists(output);
		} catch (IOException ignored) {
			// ignored
		}
	}

	// A faster exists check
	private static boolean exists(Path path) {
		return path.getFileSystem() == FileSystems.getDefault() ? path.toFile().exists() : Files.exists(path);
	}

	private static Optional<String> readAttribute(Path path, String key) throws IOException {
		final UserDefinedFileAttributeView attributeView = Files.getFileAttributeView(path, UserDefinedFileAttributeView.class);

		if (!attributeView.list().contains(key)) {
			return Optional.empty();
		}

		final ByteBuffer buffer = ByteBuffer.allocate(attributeView.size(key));
		attributeView.read(key, buffer);
		buffer.flip();
		final String value = StandardCharsets.UTF_8.decode(buffer).toString();
		return Optional.of(value);
	}

	private static void writeAttribute(Path path, String key, String value) throws IOException {
		// TODO may need to fallback to creating a separate file if this isnt supported.
		final UserDefinedFileAttributeView attributeView = Files.getFileAttributeView(path, UserDefinedFileAttributeView.class);
		final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		final ByteBuffer buffer = ByteBuffer.wrap(bytes);
		final int written = attributeView.write(key, buffer);
		assert written == bytes.length;
	}

	private FileTime getLastModified(Path path) throws IOException {
		final BasicFileAttributeView basicView = Files.getFileAttributeView(path, BasicFileAttributeView.class);
		return basicView.readAttributes().lastModifiedTime();
	}

	private RuntimeException error(String message, Object... args) {
		return new CompletionException(new DownloadException(String.format(Locale.ENGLISH, message, args)));
	}

	private RuntimeException error(Throwable throwable) {
		return new CompletionException(new DownloadException(throwable));
	}

	private RuntimeException error(Throwable throwable, String message, Object... args) {
		return new CompletionException(new DownloadException(message.formatted(args), throwable));
	}
}
