/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021 FabricMC
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

package net.fabricmc.loom.configuration.providers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.util.FileSystemUtil;

public record BundleMetadata(List<Entry> libraries, List<Entry> versions, String mainClass) {
	private static final String LIBRARIES_LIST_PATH = "META-INF/libraries.list";
	private static final String VERSIONS_LIST_PATH = "META-INF/versions.list";
	private static final String MAINCLASS_PATH = "META-INF/main-class";

	@Nullable
	public static BundleMetadata fromJar(Path jar) throws IOException {
		final List<Entry> libraries;
		final List<Entry> versions;
		final String mainClass;

		try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(jar)) {
			if (!Files.exists(fs.get().getPath(VERSIONS_LIST_PATH))) {
				// Legacy jar
				return null;
			}

			libraries = readEntries(fs.readString(LIBRARIES_LIST_PATH), "META-INF/libraries/");
			versions = readEntries(fs.readString(VERSIONS_LIST_PATH), "META-INF/versions/");
			mainClass = fs.readString(MAINCLASS_PATH).trim();
		}

		return new BundleMetadata(libraries, versions, mainClass);
	}

	private static List<Entry> readEntries(String content, String pathPrefix) {
		List<Entry> entries = new ArrayList<>();

		for (String entry : content.split("\n")) {
			if (entry.isBlank()) {
				continue;
			}

			String[] split = entry.split("\t");

			if (split.length != 3) {
				continue;
			}

			entries.add(new Entry(split[0], split[1], pathPrefix + split[2]));
		}

		return Collections.unmodifiableList(entries);
	}

	public record Entry(String sha1, String name, String path) {
		public void unpackEntry(Path jar, Path dest) throws IOException {
			try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(jar);
					InputStream is = Files.newInputStream(fs.get().getPath(path()))) {
				Files.copy(is, dest, StandardCopyOption.REPLACE_EXISTING);
			}
		}
	}
}
