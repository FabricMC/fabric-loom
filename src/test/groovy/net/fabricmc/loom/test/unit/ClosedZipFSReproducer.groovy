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

package net.fabricmc.loom.test.unit

import java.nio.file.FileSystem
import java.nio.file.FileSystemAlreadyExistsException
import java.nio.file.FileSystemException
import java.nio.file.FileSystemNotFoundException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path

import spock.lang.Specification

// Test to prove https://bugs.openjdk.org/browse/JDK-8291712
// If this test starts failing on a new JDK, it is likely that the bug has been fixed!
class ClosedZipFSReproducer extends Specification {
	def "JDK-8291712"() {
		when:
		Path tempDir = Files.createTempDirectory("test")
		Path zipFile = tempDir.resolve("example.zip")

		// Create a new ZipFileSystem, and prevent it from being written on close
		def fs = openZipFS(zipFile, true)
		Files.writeString(fs.getPath("test.txt"), "Hello, World!")

		// Before we close the ZipFS do something to prevent the zip from being written on close
		// E.G lock the file
		Files.delete(zipFile)
		Files.createDirectories(zipFile)
		Files.createFile(zipFile.resolve("lock"))

		try {
			fs.close()
			throw new IllegalStateException("Expected FileSystemException")
		} catch (FileSystemException ignored) {
			// Expected
		}

		// Remove the "lock"
		Files.delete(zipFile.resolve("lock"))

		// We would expect a new FileSystem to be created, but instead we get the old one
		// That is in a broken state
		fs = openZipFS(zipFile, true)

		then:
		!fs.isOpen()
	}

	private static FileSystem openZipFS(Path path, boolean create) throws IOException {
		URI uri = toJarUri(path)
		try {
			return FileSystems.getFileSystem(uri)
		} catch (FileSystemNotFoundException e) {
			try {
				return FileSystems.newFileSystem(uri, create ? Collections.singletonMap("create", "true") : Collections.emptyMap())
			} catch (FileSystemAlreadyExistsException f) {
				return FileSystems.getFileSystem(uri)
			}
		}
	}

	private static URI toJarUri(Path path) {
		URI uri = path.toUri()

		try {
			return new URI("jar:" + uri.getScheme(), uri.getHost(), uri.getPath(), uri.getFragment())
		} catch (URISyntaxException e) {
			throw new RuntimeException("can't convert path " + path + " to uri", e)
		}
	}
}
