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
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import org.gradle.api.JavaVersion;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.util.CompletableFutureCollector;
import net.fabricmc.loom.util.FileSystemUtil;

public final class JarWalker {
	private static final Logger LOGGER = LoggerFactory.getLogger(JarWalker.class);

	private JarWalker() {
	}

	public static List<ClassEntry> findClasses(Path jar) throws IOException {
		try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(jar)) {
			return findClasses(fs);
		}
	}

	public static List<ClassEntry> findClasses(FileSystemUtil.Delegate fs) throws IOException {
		List<String> outerClasses = new ArrayList<>();
		Map<String, List<String>> innerClasses = new HashMap<>();

		// Iterate over all the classes in the jar, and store them into the sorted list.
		try (Stream<Path> walk = Files.walk(fs.getRoot())) {
			Iterator<Path> iterator = walk.iterator();

			while (iterator.hasNext()) {
				final Path entry = iterator.next();

				if (!Files.isRegularFile(entry)) {
					continue;
				}

				final String fileName = entry.toString().substring(fs.getRoot().toString().length());

				if (!fileName.endsWith(".class")) {
					continue;
				}

				String outerClass = findOuterClass(fs, fileName);

				if (outerClass == null) {
					outerClasses.add(fileName);
				} else {
					innerClasses.computeIfAbsent(outerClass + ".class", k -> new ArrayList<>()).add(fileName);
				}
			}
		}

		LOGGER.info("Found {} outer classes and {} inner classes", outerClasses.size(), innerClasses.size());

		Collections.sort(outerClasses);

		final Executor executor = getExecutor();
		List<CompletableFuture<ClassEntry>> classEntries = new ArrayList<>();

		for (String outerClass : outerClasses) {
			List<String> innerClasList = innerClasses.get(outerClass);

			if (innerClasList == null) {
				innerClasList = Collections.emptyList();
			} else {
				Collections.sort(innerClasList);
			}

			classEntries.add(getClassEntry(outerClass, innerClasList, fs, executor));
		}

		try {
			return classEntries.stream()
					.collect(CompletableFutureCollector.allOf())
					.get(10, TimeUnit.MINUTES);
		} catch (InterruptedException | ExecutionException | TimeoutException e) {
			throw new RuntimeException("Failed to get class entries", e);
		}
	}

	/**
	 * Check if the given class file denotes and inner class and find the corresponding outer class name.
	 */
	private static String findOuterClass(FileSystemUtil.Delegate fs, String classFile) throws IOException {
		// this check can speed things up quite a bit, even if it does not follow the JVM spec
		if (classFile.indexOf('$') < 0) {
			return null;
		}

		try (InputStream is = Files.newInputStream(fs.getPath(classFile))) {
			final ClassReader reader = new ClassReader(is);
			final ClassNode classNode = new ClassNode();

			reader.accept(classNode, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);

			for (InnerClassNode innerClass : classNode.innerClasses) {
				// a class file also contains references to enclosed inner classes
				if (innerClass.name.equals(classNode.name)) {
					// only regular inner classes have the outer class in the inner class attribute
					if (innerClass.outerName != null) {
						return innerClass.outerName;
					}

					// local and anonymous classes have the outer class in the enclosing method attribute
					// we check for both attributes because both should be present for decompilers to
					// recognize a class as an inner class
					if (classNode.outerClass != null) {
						return classNode.outerClass;
					}

					// there are some Minecraft versions with one attribute stripped but not the other
					LOGGER.debug("inner class attribute is present for " + classNode.name + " but no outer class could be found, weird!");
				}
			}
		}

		return null;
	}

	private static CompletableFuture<ClassEntry> getClassEntry(String outerClass, List<String> innerClasses, FileSystemUtil.Delegate fs, Executor executor) {
		List<CompletableFuture<List<String>>> parentClassesFutures = new ArrayList<>();

		// Get the super classes of the outer class and any inner classes
		parentClassesFutures.add(CompletableFuture.supplyAsync(() -> getSuperClasses(outerClass, fs), executor));

		for (String innerClass : innerClasses) {
			parentClassesFutures.add(CompletableFuture.supplyAsync(() -> getSuperClasses(innerClass, fs), executor));
		}

		return parentClassesFutures.stream()
				.collect(CompletableFutureCollector.allOf())
				.thenApply(lists -> lists.stream()
						.flatMap(List::stream)
						.filter(JarWalker::isNotReservedClass)
						.distinct()
						.toList())
				.thenApply(parentClasses -> new ClassEntry(outerClass, innerClasses, parentClasses));
	}

	private static List<String> getSuperClasses(String classFile, FileSystemUtil.Delegate fs) {
		try (InputStream is = Files.newInputStream(fs.getPath(classFile))) {
			final ClassReader reader = new ClassReader(is);

			List<String> parentClasses = new ArrayList<>();
			String superName = reader.getSuperName();

			if (superName != null) {
				parentClasses.add(superName);
			}

			Collections.addAll(parentClasses, reader.getInterfaces());
			return Collections.unmodifiableList(parentClasses);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to read class file: " + classFile, e);
		}
	}

	private static Executor getExecutor() {
		if (JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_21)) {
			try {
				Method m = Executors.class.getMethod("newVirtualThreadPerTaskExecutor");
				return (ExecutorService) m.invoke(null);
			} catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
				throw new RuntimeException("Failed to create virtual thread executor", e);
			}
		}

		return ForkJoinPool.commonPool();
	}

	// Slight optimization, if we skip over Object
	private static boolean isNotReservedClass(String name) {
		return !"java/lang/Object".equals(name);
	}
}
