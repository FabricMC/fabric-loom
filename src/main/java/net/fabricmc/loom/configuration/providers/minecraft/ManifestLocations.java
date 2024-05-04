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

package net.fabricmc.loom.configuration.providers.minecraft;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.Queue;

import net.fabricmc.loom.api.manifest.VersionsManifestsAPI;
import net.fabricmc.loom.configuration.providers.minecraft.ManifestLocations.ManifestLocation;

public class ManifestLocations implements VersionsManifestsAPI, Iterable<ManifestLocation> {
	private static final String FILE_EXTENSION = ".json";
	private final Queue<ManifestLocation> locations = new PriorityQueue<>();
	private final String baseFileName;

	public ManifestLocations(String baseFileName) {
		this.baseFileName = baseFileName;
	}

	public void addBuiltIn(int priority, String url, String fileName) {
		locations.add(new ManifestLocation(priority, url, fileName));
	}

	@Override
	public void add(String url, int priority) {
		locations.add(new ManifestLocation(priority, url));
	}

	@Override
	public Iterator<ManifestLocation> iterator() {
		return locations.iterator();
	}

	public class ManifestLocation implements Comparable<ManifestLocation> {
		private final int priority;
		private final String url;
		private final String builtInFileName;

		private ManifestLocation(int priority, String url) {
			this(priority, url, null);
		}

		private ManifestLocation(int priority, String url, String builtInFileName) {
			this.priority = priority;
			this.url = url;
			this.builtInFileName = builtInFileName;
		}

		public boolean isBuiltIn() {
			return builtInFileName != null;
		}

		public String url() {
			return url;
		}

		public Path cacheFile(Path dir) {
			String fileName = (builtInFileName != null)
					? builtInFileName + FILE_EXTENSION
					: baseFileName + "-" + Integer.toHexString(url.hashCode()) + FILE_EXTENSION;
			return dir.resolve(fileName);
		}

		@Override
		public int compareTo(ManifestLocation o) {
			return Integer.compare(priority, o.priority);
		}
	}
}
