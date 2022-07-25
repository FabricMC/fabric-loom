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

import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DownloadExecutor implements AutoCloseable {
	private final ExecutorService executorService;
	private final List<DownloadException> downloadExceptions = Collections.synchronizedList(new ArrayList<>());

	public DownloadExecutor(int threads) {
		executorService = Executors.newFixedThreadPool(threads);
	}

	void runAsync(DownloadRunner downloadRunner) {
		if (!downloadExceptions.isEmpty()) {
			return;
		}

		executorService.execute(() -> {
			try {
				downloadRunner.run();
			} catch (DownloadException e) {
				executorService.shutdownNow();
				downloadExceptions.add(e);
				throw new UncheckedIOException(e);
			}
		});
	}

	@Override
	public void close() throws DownloadException {
		executorService.shutdown();

		try {
			executorService.awaitTermination(1, TimeUnit.DAYS);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}

		if (!downloadExceptions.isEmpty()) {
			DownloadException downloadException = new DownloadException("Failed to download");

			for (DownloadException suppressed : downloadExceptions) {
				downloadException.addSuppressed(suppressed);
			}

			throw downloadException;
		}
	}

	@FunctionalInterface
	public interface DownloadRunner {
		void run() throws DownloadException;
	}
}
