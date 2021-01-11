package net.fabricmc.loom.util;

import org.gradle.internal.impldep.com.google.api.client.repackaged.com.google.common.base.Function;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

public class ThreadingUtils {
	public static <T> void run(Collection<T> values, UnsafeConsumer<T> action) {
		run(values.stream()
				.<UnsafeRunnable>map(t -> () -> action.accept(t))
				.collect(Collectors.toList()));
	}

	public static void run(UnsafeRunnable... jobs) {
		run(Arrays.asList(jobs));
	}

	public static void run(Collection<UnsafeRunnable> jobs) {
		try {
			ExecutorService service = Executors.newFixedThreadPool(Math.min(jobs.size(), Runtime.getRuntime().availableProcessors() / 2));
			List<Future<?>> futures = new LinkedList<>();
			for (UnsafeRunnable runnable : jobs) {
				futures.add(service.submit(() -> {
					try {
						runnable.run();
					} catch (Throwable throwable) {
						throw new RuntimeException(throwable);
					}
				}));
			}
			for (Future<?> future : futures) {
				future.get();
			}
			service.shutdownNow();
		} catch (InterruptedException | ExecutionException e) {
			throw new RuntimeException(e);
		}
	}

	public static <T, R> List<R> get(Collection<T> values, Function<T, R> action) {
		return get(values.stream()
				.<UnsafeCallable<R>>map(t -> () -> action.apply(t))
				.collect(Collectors.toList()));
	}

	@SafeVarargs
	public static <T> List<T> get(UnsafeCallable<T>... jobs) {
		return get(Arrays.asList(jobs));
	}

	public static <T> List<T> get(Collection<UnsafeCallable<T>> jobs) {
		try {
			ExecutorService service = Executors.newFixedThreadPool(Math.min(jobs.size(), Runtime.getRuntime().availableProcessors() / 2));
			List<Future<T>> futures = new LinkedList<>();
			for (UnsafeCallable<T> runnable : jobs) {
				futures.add(service.submit(() -> {
					try {
						return runnable.call();
					} catch (Throwable throwable) {
						throw new RuntimeException(throwable);
					}
				}));
			}
			List<T> result = new ArrayList<>();
			for (Future<T> future : futures) {
				result.add(future.get());
			}
			service.shutdownNow();
			return result;
		} catch (InterruptedException | ExecutionException e) {
			throw new RuntimeException(e);
		}
	}

	public interface UnsafeRunnable {
		void run() throws Throwable;
	}

	public interface UnsafeCallable<T> {
		T call() throws Throwable;
	}

	public interface UnsafeConsumer<T> {
		void accept(T value) throws Throwable;
	}
}
