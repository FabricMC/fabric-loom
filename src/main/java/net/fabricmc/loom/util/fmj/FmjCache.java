/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 FabricMC
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

package net.fabricmc.loom.util.fmj;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import net.fabricmc.loom.util.CompletableFutureCollector;

public class FmjCache {
	private static final Executor EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

	private final Map<String, CompletableFuture<List<FabricModJson>>> cache = new ConcurrentHashMap<>();

	public CompletableFuture<List<FabricModJson>> get(String cacheKey, Supplier<List<FabricModJson>> supplier) {
		return cache.computeIfAbsent(cacheKey, $ -> CompletableFuture.supplyAsync(supplier, EXECUTOR));
	}

	public List<FabricModJson> getBlocking(String cacheKey, Supplier<List<FabricModJson>> supplier) {
		return join(get(cacheKey, supplier));
	}

	public static List<FabricModJson> joinAll(Collection<CompletableFuture<List<FabricModJson>>> futures) {
		return join(futures.stream()
				.collect(CompletableFutureCollector.allOf()))
				.stream()
				.flatMap(List::stream)
				.toList();
	}

	// Rethrows the exception from the CompletableFuture, if it exists.
	public static <T> T join(CompletableFuture<T> future) {
		try {
			return future.join();
		} catch (CompletionException e) {
			sneakyThrow(e.getCause() != null ? e.getCause() : e);
			throw new IllegalStateException();
		}
	}

	private static <E extends Throwable> void sneakyThrow(Throwable e) throws E {
		//noinspection unchecked
		throw (E) e;
	}
}
