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

package net.fabricmc.loom.util;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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

import org.gradle.api.JavaVersion;

public interface AsyncZipProcessor {
	static void processEntries(Path inputZip, Path outputZip, AsyncZipProcessor processor) throws IOException {
		try (FileSystemUtil.Delegate inFs = FileSystemUtil.getJarFileSystem(inputZip, false);
				FileSystemUtil.Delegate outFs = FileSystemUtil.getJarFileSystem(outputZip, true)) {
			final Path inRoot = inFs.get().getPath("/");
			final Path outRoot = outFs.get().getPath("/");

			List<CompletableFuture<Void>> futures = new ArrayList<>();
			final ExecutorService executor = getExecutor();

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
					});

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

	/**
	 * On Java 21 return the virtual thread pool, otherwise return a fixed thread pool.
	 */
	private static ExecutorService getExecutor() {
		if (JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_21)) {
			// I'm not sure if this is actually faster, but its fun to use loom in loom :D
			try {
				Method m = Executors.class.getMethod("newVirtualThreadPerTaskExecutor");
				return (ExecutorService) m.invoke(null);
			} catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
				throw new RuntimeException("Failed to create virtual thread executor", e);
			}
		}

		return Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
	}

	void processEntryAsync(Path inputEntry, Path outputEntry) throws IOException;
}
