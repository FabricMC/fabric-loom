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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An index of all packages and the classes directly contained within.
 */
public record JarPackageIndex(Map<String, List<String>> packages) {
	public static JarPackageIndex create(List<Path> jars) {
		Map<String, List<String>> packages = jars.stream()
				.map(jar -> CompletableFuture.supplyAsync(() -> {
					try {
						List<String> classes = getClasses(jar);
						return groupClassesByPackage(classes);
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				}, Executors.newVirtualThreadPerTaskExecutor()))
				.map(CompletableFuture::join)
				.flatMap(map -> map.entrySet().stream())
				.collect(Collectors.toMap(
						Map.Entry::getKey,
						Map.Entry::getValue,
						(existing, newValues) -> {
							existing.addAll(newValues);
							return existing;
						}
				));

		return new JarPackageIndex(packages);
	}

	private static List<String> getClasses(Path jar) throws IOException {
		try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(jar, false);
				Stream<Path> walk = Files.walk(fs.getRoot())) {
			return walk
					.filter(Files::isRegularFile)
					.map(Path::toString)
					.filter(className -> className.endsWith(".class"))
					.map(className -> className.startsWith("/") ? className.substring(1) : className)
					.toList();
		}
	}

	private static Map<String, List<String>> groupClassesByPackage(List<String> classes) {
		return classes.stream()
				.filter(className -> className.endsWith(".class")) // Ensure it's a class file
				.collect(Collectors.groupingBy(
						JarPackageIndex::extractPackageName,
						Collectors.mapping(
								JarPackageIndex::extractClassName,
								Collectors.toList()
						)
				));
	}

	// Returns the package name from a class name, e.g., "com/example/MyClass.class" -> "com.example"
	private static String extractPackageName(String className) {
		int lastSlashIndex = className.lastIndexOf('/');
		return lastSlashIndex == -1 ? "" : className.substring(0, lastSlashIndex).replace("/", ".");
	}

	private static String extractClassName(String className) {
		int lastSlashIndex = className.lastIndexOf('/');
		String simpleName = lastSlashIndex == -1 ? className : className.substring(lastSlashIndex + 1);
		return simpleName.endsWith(".class") ? simpleName.substring(0, simpleName.length() - 6) : simpleName;
	}
}
