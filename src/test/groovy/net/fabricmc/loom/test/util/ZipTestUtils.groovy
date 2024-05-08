/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2023 FabricMC
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

package net.fabricmc.loom.test.util

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.Attributes
import java.util.jar.Manifest

import net.fabricmc.loom.util.FileSystemUtil

class ZipTestUtils {
	static Path createZip(Map<String, String> entries, String suffix = ".zip") {
		return createZipFromBytes(entries.collectEntries { k, v ->
			[
				k,
				v.getBytes(StandardCharsets.UTF_8)
			]
		}, suffix)
	}

	static Path createZipFromBytes(Map<String, byte[]> entries, String suffix = ".zip") {
		def file = Files.createTempFile("loom-test", suffix)
		Files.delete(file)

		FileSystemUtil.getJarFileSystem(file, true).withCloseable { zip ->
			entries.forEach { path, value ->
				def fsPath = zip.getPath(path)
				def fsPathParent = fsPath.getParent()
				if (fsPathParent != null) Files.createDirectories(fsPathParent)
				Files.write(fsPath, value)
			}
		}

		return file
	}

	static String manifest(String key, String value) {
		return manifest(Map.of(key, value))
	}

	static String manifest(Map<String, String> entries) {
		def manifest = new Manifest()
		manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0")
		entries.forEach { key, value ->
			manifest.getMainAttributes().putValue(key, value)
		}

		def out = new ByteArrayOutputStream()
		manifest.write(out)
		return out.toString(StandardCharsets.UTF_8)
	}
}
