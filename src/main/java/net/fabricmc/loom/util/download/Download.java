/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022-2024 FabricMC
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

import static com.google.common.io.Files.createParentDirs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntConsumer;
import java.util.zip.GZIPInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.util.AttributeHelper;
import net.fabricmc.loom.util.Checksum;

public final class Download {
	private static final String E_TAG = "ETag";
	private static final Logger LOGGER = LoggerFactory.getLogger(Download.class);
	private static final Duration TIMEOUT = Duration.ofMinutes(1);
	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.ALWAYS)
			.proxy(ProxySelector.getDefault())
			.connectTimeout(TIMEOUT)
			.build();

	public static DownloadBuilder create(String url) throws URISyntaxException {
		return DownloadBuilder.create(url);
	}

	private final URI url;
	private final String expectedHash;
	private final boolean useEtag;
	private final boolean forceDownload;
	private final boolean offline;
	private final Duration maxAge;
	private final DownloadProgressListener progressListener;
	private final HttpClient.Version httpVersion;
	private final int downloadAttempt;

	Download(URI url, String expectedHash, boolean useEtag, boolean forceDownload, boolean offline, Duration maxAge, DownloadProgressListener progressListener, HttpClient.Version httpVersion, int downloadAttempt) {
		this.url = url;
		this.expectedHash = expectedHash;
		this.useEtag = useEtag;
		this.forceDownload = forceDownload;
		this.offline = offline;
		this.maxAge = maxAge;
		this.progressListener = progressListener;
		this.httpVersion = httpVersion;
		this.downloadAttempt = downloadAttempt;
	}

	private HttpRequest.Builder requestBuilder() {
		return HttpRequest.newBuilder(url)
				.timeout(TIMEOUT)
				.version(httpVersion)
				.GET();
	}

	private HttpRequest getRequest() {
		return requestBuilder()
				.build();
	}

	private HttpRequest getETagRequest(String etag) {
		return requestBuilder()
				.header("If-None-Match", etag)
				.build();
	}

	private <T> HttpResponse<T> send(HttpRequest httpRequest, HttpResponse.BodyHandler<T> bodyHandler) throws DownloadException {
		if (offline) {
			throw error("Unable to download %s in offline mode", this.url);
		}

		progressListener.onStart();

		try {
			return HTTP_CLIENT.send(httpRequest, bodyHandler);
		} catch (IOException | InterruptedException e) {
			throw error(e, "Failed to download (%s)", url);
		}
	}

	String downloadString() throws DownloadException {
		final HttpResponse<InputStream> response = send(getRequest(), HttpResponse.BodyHandlers.ofInputStream());
		final int statusCode = response.statusCode();
		final boolean successful = statusCode >= 200 && statusCode < 300;

		if (!successful) {
			progressListener.onEnd();
			throw statusError("HTTP request to (%s) returned unsuccessful status".formatted(url) + "(%d)", statusCode);
		}

		try (InputStream inputStream = decodeOutput(response)) {
			return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw error(e, "Failed to decode download output");
		} finally {
			progressListener.onEnd();
		}
	}

	void downloadPath(Path output) throws DownloadException {
		boolean downloadRequired = requiresDownload(output);

		if (!downloadRequired) {
			// Does not require download, we are done here.
			progressListener.onEnd();
			return;
		}

		try {
			doDownload(output);
		} catch (Throwable throwable) {
			tryCleanup(output);
			throw error(throwable, "Failed to download file from (%s) to (%s)", url, output);
		} finally {
			progressListener.onEnd();
		}
	}

	private void doDownload(Path output) throws DownloadException {
		Optional<String> eTag = Optional.empty();

		if (!forceDownload && useEtag && exists(output)) {
			eTag = readEtag(output);
		}

		try {
			createParentDirs(output.toFile());
		} catch (IOException e) {
			throw error(e, "Failed to create parent directories");
		}

		final HttpRequest httpRequest = eTag
				.map(this::getETagRequest)
				.orElseGet(this::getRequest);

		// Create a .lock file, this allows us to re-download if the download was forcefully aborted part way through.
		createLock(output);
		HttpResponse<InputStream> response = send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
		getAndResetLock(output);

		final int statusCode = response.statusCode();
		boolean success = statusCode == HttpURLConnection.HTTP_NOT_MODIFIED || (statusCode >= 200 && statusCode < 300);

		if (statusCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
			try {
				// Update the last modified time so we don't retry the request until the max age has passed again.
				Files.setLastModifiedTime(output, FileTime.from(Instant.now()));
			} catch (IOException e) {
				throw error(e, "Failed to update last modified time");
			}

			// Success, etag matched.
			return;
		}

		if (!success) {
			throw statusError("HTTP request returned unsuccessful status (%d)", statusCode);
		}

		downloadToPath(output, response);

		if (useEtag) {
			final HttpHeaders headers = response.headers();
			final String responseETag = headers.firstValue(E_TAG.toLowerCase(Locale.ROOT)).orElse(null);

			if (responseETag != null) {
				writeEtag(output, responseETag);
			}
		}

		if (expectedHash != null) {
			// Ensure we downloaded the expected hash.
			if (!isHashValid(output)) {
				String downloadedHash;

				try {
					downloadedHash = Checksum.sha1Hex(output);
					Files.deleteIfExists(output);
				} catch (IOException e) {
					downloadedHash = "unknown hash";
				}

				throw error("Failed to download (%s) with expected hash: %s got %s", url, expectedHash, downloadedHash);
			}

			// Write the hash to the file attribute, saves a lot of time trying to re-compute the hash when re-visiting this file.
			writeHash(output, expectedHash);
		}
	}

	private void downloadToPath(Path output, HttpResponse<InputStream> response) throws DownloadException {
		// Download the file initially to a .part file
		final Path partFile = getPartFile(output);

		try {
			Files.deleteIfExists(output);
			Files.deleteIfExists(partFile);
		} catch (IOException e) {
			throw error(e, "Failed to delete existing file");
		}

		final long length = Long.parseLong(response.headers().firstValue("Content-Length").orElse("-1"));
		AtomicLong totalBytes = new AtomicLong(0);

		try (OutputStream outputStream = Files.newOutputStream(partFile, StandardOpenOption.CREATE_NEW)) {
			copyWithCallback(decodeOutput(response), outputStream, value -> {
				if (length < 0) {
					return;
				}

				progressListener.onProgress(totalBytes.addAndGet(value), length);
			});
		} catch (IOException e) {
			throw error(e, "Failed to decode and write download output");
		}

		if (Files.notExists(partFile)) {
			throw error("No file was downloaded");
		}

		if (length > 0) {
			try {
				final long actualLength = Files.size(partFile);

				if (actualLength != length) {
					throw error("Unexpected file length of %d bytes, expected %d bytes".formatted(actualLength, length));
				}
			} catch (IOException e) {
				throw error(e);
			}
		}

		try {
			// Once the file has been fully read, create a hard link to the destination file.
			// And then remove the temporary file, this ensures that the output file only exists in fully populated state.
			Files.createLink(output, partFile);
			Files.delete(partFile);
		} catch (IOException e) {
			throw error(e, "Failed to complete download");
		}
	}

	private void copyWithCallback(InputStream is, OutputStream os, IntConsumer consumer) throws IOException {
		byte[] buffer = new byte[1024];
		int length;

		while ((length = is.read(buffer)) > 0) {
			os.write(buffer, 0, length);
			consumer.accept(length);
		}
	}

	private InputStream decodeOutput(HttpResponse<InputStream> response) throws IOException {
		final String encoding = response.headers().firstValue("Content-Encoding").orElse("");

		return switch (encoding) {
		case "gzip" -> new GZIPInputStream(response.body());
		case "" -> response.body();
		default -> throw error("Unsupported encoding: %s", encoding);
		};
	}

	private boolean requiresDownload(Path output) throws DownloadException {
		final boolean locked = getAndResetLock(output);

		if (forceDownload || !exists(output)) {
			// File does not exist, or we are forced to download again.
			return true;
		}

		if (locked && downloadAttempt == 1) {
			LOGGER.warn("Forcing downloading {} as existing lock file was found. This may happen if the gradle build was forcefully canceled.", output);
			return true;
		}

		if (offline) {
			// We know the file exists, nothing more we can do.
			return false;
		}

		if (expectedHash != null) {
			final String hashAttribute = readHash(output).orElse("");

			if (expectedHash.equalsIgnoreCase(hashAttribute)) {
				// File has a matching hash attribute, assume file intact.
				return false;
			}

			if (isHashValid(output)) {
				// Valid hash, no need to re-download
				writeHash(output, expectedHash);
				return false;
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

	private boolean isOutdated(Path path) throws DownloadException {
		try {
			final FileTime lastModified = Files.getLastModifiedTime(path);
			return lastModified.toInstant()
					.isBefore(Instant.now().minus(maxAge));
		} catch (IOException e) {
			throw error(e, "Failed to check if (%s) is outdated", path);
		}
	}

	private Optional<String> readEtag(Path output) {
		try {
			return AttributeHelper.readAttribute(output, E_TAG);
		} catch (IOException e) {
			return Optional.empty();
		}
	}

	private void writeEtag(Path output, String eTag) throws DownloadException {
		try {
			AttributeHelper.writeAttribute(output, E_TAG, eTag);
		} catch (IOException e) {
			throw error(e, "Failed to write etag to (%s)", output);
		}
	}

	private Optional<String> readHash(Path output) {
		try {
			return AttributeHelper.readAttribute(output, "LoomHash");
		} catch (IOException e) {
			return Optional.empty();
		}
	}

	private void writeHash(Path output, String value) throws DownloadException {
		try {
			AttributeHelper.writeAttribute(output, "LoomHash", value);
		} catch (IOException e) {
			throw error(e, "Failed to write hash to (%s)", output);
		}
	}

	private void tryCleanup(Path output) {
		try {
			Files.deleteIfExists(output);
		} catch (IOException ignored) {
			// ignored
		}

		try {
			Files.deleteIfExists(getLockFile(output));
		} catch (IOException ignored) {
			// ignored
		}

		try {
			Files.deleteIfExists(getPartFile(output));
		} catch (IOException ignored) {
			// ignored
		}
	}

	// A faster exists check
	private static boolean exists(Path path) {
		return path.getFileSystem() == FileSystems.getDefault() ? path.toFile().exists() : Files.exists(path);
	}

	private Path getLockFile(Path output) {
		return output.resolveSibling(output.getFileName() + ".lock");
	}

	private Path getPartFile(Path output) {
		return output.resolveSibling(output.getFileName() + ".part");
	}

	private boolean getAndResetLock(Path output) throws DownloadException {
		final Path lock = getLockFile(output);
		final boolean exists = exists(lock);

		if (exists) {
			try {
				Files.delete(lock);
			} catch (IOException e) {
				throw error(e, "Failed to release lock on %s", lock);
			}
		}

		return exists;
	}

	private void createLock(Path output) throws DownloadException {
		final Path lock = getLockFile(output);

		try {
			Files.createFile(lock);
		} catch (IOException e) {
			throw error(e, "Failed to acquire lock on %s", lock);
		}
	}

	private DownloadException statusError(String message, int statusCode) {
		return new DownloadException(String.format(Locale.ENGLISH, message, statusCode), statusCode);
	}

	private DownloadException error(String message, Object... args) {
		return new DownloadException(String.format(Locale.ENGLISH, message, args));
	}

	private DownloadException error(Throwable throwable) {
		return new DownloadException(throwable);
	}

	private DownloadException error(Throwable throwable, String message, Object... args) {
		return new DownloadException(message.formatted(args), throwable);
	}
}
