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

package net.fabricmc.loom.util;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public interface AsyncZipProcessor {
	static void processEntries(Path inputZip, Path outputZip, AsyncZipProcessor processor) throws IOException {
		try (FileSystemUtil.Delegate inFs = FileSystemUtil.getJarFileSystem(inputZip, false);
				FileSystemUtil.Delegate outFs = FileSystemUtil.getJarFileSystem(outputZip, true)) {
			final Path inRoot = inFs.get().getPath("/");
			final Path outRoot = outFs.get().getPath("/");

			List<CompletableFuture<Void>> futures = new ArrayList<>();
			final ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

			Files.walkFileTree(inRoot, new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult visitFile(Path inputFile, BasicFileAttributes attrs) throws IOException {
					final CompletableFuture<Void> future = CompletableFuture.supplyAsync(() -> {
						try {
							final String rel = inRoot.relativize(inputFile).toString();
							final Path outputFile = outRoot.resolve(rel);
							processor.processEntryAsync(inputFile, outputFile);
						} catch (IOException e) {
							throw new CompletionException(e);
						}

						return null;
					}, executor);

					futures.add(future);
					return FileVisitResult.CONTINUE;
				}
			});

			// Wait for all futures to complete
			for (CompletableFuture<Void> future : futures) {
				try {
					future.join();
				} catch (CompletionException e) {
					if (e.getCause() instanceof IOException ioe) {
						throw ioe;
					}

					throw new RuntimeException("Failed to process zip", e.getCause());
				}
			}

			executor.shutdown();
		}
	}

	void processEntryAsync(Path inputEntry, Path outputEntry) throws IOException;
}
