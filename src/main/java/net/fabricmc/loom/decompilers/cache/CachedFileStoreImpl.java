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

package net.fabricmc.loom.decompilers.cache;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;

public record CachedFileStoreImpl<T>(Path root, EntrySerializer<T> entrySerializer, CacheRules cacheRules) implements CachedFileStore<T> {
	public CachedFileStoreImpl {
		Objects.requireNonNull(root, "root");
	}

	@Override
	public @Nullable T getEntry(String key) throws IOException {
		Path path = resolve(key);

		if (Files.notExists(path)) {
			return null;
		}

		// Update last modified, so recently used files stay in the cache
		Files.setLastModifiedTime(path, FileTime.from(Instant.now()));
		return entrySerializer.read(path);
	}

	@Override
	public void putEntry(String key, T data) throws IOException {
		Path path = resolve(key);
		Files.createDirectories(path.getParent());
		entrySerializer.write(data, path);
	}

	private Path resolve(String key) {
		return root.resolve(key);
	}

	public void prune() throws IOException {
		// Sorted oldest -> newest
		List<PathEntry> entries = new ArrayList<>();

		// Iterate over all the files in the cache, and store them into the sorted list.
		try (Stream<Path> walk = Files.walk(root)) {
			Iterator<Path> iterator = walk.iterator();

			while (iterator.hasNext()) {
				final Path entry = iterator.next();

				if (!Files.isRegularFile(entry)) {
					continue;
				}

				insertSorted(entries, new PathEntry(entry));
			}
		}

		// Delete the oldest files to get under the max file limit
		if (entries.size() > cacheRules.maxFiles) {
			for (int i = 0; i < cacheRules.maxFiles; i++) {
				PathEntry toRemove = entries.remove(0);
				Files.delete(toRemove.path);
			}
		}

		final Instant maxAge = Instant.now().minus(cacheRules().maxAge());
		Iterator<PathEntry> iterator = entries.iterator();

		while (iterator.hasNext()) {
			final PathEntry entry = iterator.next();

			if (entry.lastModified().toInstant().isAfter(maxAge)) {
				// File is not longer than the max age
				// As this is a sorted list we don't need to keep checking
				break;
			}

			// Remove all files over the max age
			iterator.remove();
			Files.delete(entry.path);
		}
	}

	private void insertSorted(List<PathEntry> list, PathEntry entry) {
		int index = Collections.binarySearch(list, entry, Comparator.comparing(PathEntry::lastModified));

		if (index < 0) {
			index = -index - 1;
		}

		list.add(index, entry);
	}

	/**
	 * The rules for the cache.
	 *
	 * @param maxFiles The maximum number of files in the cache
	 * @param maxAge  The maximum age of a file in the cache
	 */
	public record CacheRules(long maxFiles, Duration maxAge) {
	}

	record PathEntry(Path path, FileTime lastModified) {
		PathEntry(Path path) throws IOException {
			this(path, Files.getLastModifiedTime(path));
		}
	}
}
