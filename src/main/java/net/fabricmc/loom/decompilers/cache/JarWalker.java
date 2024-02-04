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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

				boolean isInnerClass = fileName.contains("$");

				if (isInnerClass) {
					String outerClassName = fileName.substring(0, fileName.indexOf('$')) + ".class";
					innerClasses.computeIfAbsent(outerClassName, k -> new ArrayList<>()).add(fileName);
				} else {
					outerClasses.add(fileName);
				}
			}
		}

		LOGGER.info("Found {} outer classes and {} inner classes", outerClasses.size(), innerClasses.size());

		Collections.sort(outerClasses);

		List<ClassEntry> classEntries = new ArrayList<>();

		for (String outerClass : outerClasses) {
			List<String> innerClasList = innerClasses.get(outerClass);

			if (innerClasList == null) {
				innerClasList = Collections.emptyList();
			} else {
				Collections.sort(innerClasList);
			}

			ClassEntry classEntry = new ClassEntry(outerClass, Collections.unmodifiableList(innerClasList));
			classEntries.add(classEntry);
		}

		return Collections.unmodifiableList(classEntries);
	}
}
