/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2026 FabricMC
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

/**
 * Shares hash-verified downloads within one build, assuming files are not modified outside Loom.
 * Refreshes and other downloads to the same path invalidate previously successful results.
 */
public final class DownloadCache {
	private final Map<Path, CachedDownload> downloads = new ConcurrentHashMap<>();

	DownloadResult download(Path output, @Nullable String hash, boolean refresh, boolean offline, DownloadExecutor.DownloadRunner runner) throws DownloadException {
		final Path path;

		try {
			path = output.toFile().getCanonicalFile().toPath();
		} catch (IOException e) {
			throw new DownloadException("Failed to resolve download path", e);
		}

		// Coordinate cache access across Loom classloaders and build services in this JVM.
		synchronized (("fabric.loom.download:" + path).intern()) {
			CachedDownload cached = downloads.get(path);

			if (hash != null && !offline && cached != null
					&& cached.hash().equalsIgnoreCase(hash) && cached.refresh() == refresh
					&& Files.isRegularFile(path)
					&& !Files.exists(path.resolveSibling(path.getFileName() + ".lock"))) {
				return cached.result();
			}

			downloads.remove(path);
			DownloadResult result = runner.run();

			if (hash != null && !offline) {
				downloads.put(path, new CachedDownload(hash, refresh, result));
			}

			return result;
		}
	}

	private record CachedDownload(String hash, boolean refresh, DownloadResult result) { }
}
