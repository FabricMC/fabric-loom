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

package net.fabricmc.loom.util.fmj;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import net.fabricmc.loom.util.ZipUtils;

/// Reads resources from a mod stored as a ZIP, directory, source resource roots,
/// or an explicit mapping of resource names to files.
public interface FabricModJsonSource {
	byte[] read(String path) throws IOException;

	record ZipSource(Path zipPath) implements FabricModJsonSource {
		@Override
		public byte[] read(String path) throws IOException {
			return ZipUtils.unpack(zipPath, path);
		}
	}

	record DirectorySource(Path directoryPath) implements FabricModJsonSource {
		@Override
		public byte[] read(String path) throws IOException {
			return Files.readAllBytes(directoryPath.resolve(path));
		}
	}

	/// Reads only the explicitly mapped resources, independently of the producer's resource directories.
	record FileMapSource(Map<String, Path> resources) implements FabricModJsonSource {
		public FileMapSource {
			resources = Map.copyOf(resources);
		}

		@Override
		public byte[] read(String path) throws IOException {
			Path file = resources.get(path);

			if (file == null) {
				throw new FileNotFoundException("Could not find: " + path);
			}

			return Files.readAllBytes(file);
		}
	}

	record SourceSetSource(Path fabricModJsonPath, List<Path> resourceRoots) implements FabricModJsonSource {
		public SourceSetSource {
			resourceRoots = List.copyOf(resourceRoots);
		}

		@Override
		public byte[] read(String path) throws IOException {
			return Files.readAllBytes(resolve(path));
		}

		/// Resolves a resource using the captured metadata path and resource-root order.
		public Path resolve(String path) throws IOException {
			if (FabricModJsonFactory.FABRIC_MOD_JSON.equals(path)) {
				return fabricModJsonPath;
			}

			for (Path resourceRoot : resourceRoots) {
				Path file = resourceRoot.resolve(path);

				if (Files.exists(file)) {
					return file;
				}
			}

			throw new FileNotFoundException("Could not find: " + path);
		}
	}
}
