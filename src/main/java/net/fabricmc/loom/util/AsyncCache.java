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

package net.fabricmc.loom.util;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class AsyncCache<T> {
	private static final Executor EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
	private final Map<Object, CompletableFuture<T>> cache = new ConcurrentHashMap<>();

	public CompletableFuture<T> get(Object cacheKey, Supplier<T> supplier) {
		return cache.computeIfAbsent(cacheKey, $ -> CompletableFuture.supplyAsync(supplier, EXECUTOR));
	}

	public T getBlocking(Object cacheKey, Supplier<T> supplier) {
		return join(get(cacheKey, supplier));
	}

	public static <T> List<T> joinList(Collection<CompletableFuture<List<T>>> futures) {
		return join(futures.stream()
				.collect(CompletableFutureCollector.allOf()))
				.stream()
				.flatMap(List::stream)
				.toList();
	}

	public static <K, V> Map<K, V> joinMap(Map<K, CompletableFuture<V>> futures) {
		return futures.entrySet().stream()
			.collect(Collectors.toMap(
					Map.Entry::getKey,
					entry -> join(entry.getValue())
			));
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

	@SuppressWarnings("unchecked")
	private static <E extends Throwable> void sneakyThrow(Throwable e) throws E {
		throw (E) e;
	}
}
