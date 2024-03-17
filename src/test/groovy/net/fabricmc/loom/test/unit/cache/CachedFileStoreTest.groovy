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

package net.fabricmc.loom.test.unit.cache

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Duration
import java.time.Instant

import spock.lang.Specification
import spock.lang.TempDir

import net.fabricmc.loom.decompilers.cache.CachedFileStore
import net.fabricmc.loom.decompilers.cache.CachedFileStoreImpl
import net.fabricmc.loom.util.FileSystemUtil

class CachedFileStoreTest extends Specification {
	@TempDir
	Path testPath

	FileSystemUtil.Delegate zipDelegate
	Path root

	void setup() {
		zipDelegate = FileSystemUtil.getJarFileSystem(testPath.resolve("cache.zip"), true)
		root = zipDelegate.get().getPath("/")
	}

	void cleanup() {
		zipDelegate.close()
	}

	def "putEntry"() {
		given:
		def cacheRules = new CachedFileStoreImpl.CacheRules(100, Duration.ofDays(7))
		def store = new CachedFileStoreImpl(root, BYTE_ARRAY_SERIALIZER, cacheRules)
		when:
		store.putEntry("abc", "Hello world".bytes)
		then:
		Files.exists(root.resolve("abc"))
	}

	def "getEntry"() {
		given:
		def cacheRules = new CachedFileStoreImpl.CacheRules(100, Duration.ofDays(7))
		def store = new CachedFileStoreImpl(root, BYTE_ARRAY_SERIALIZER, cacheRules)
		when:
		store.putEntry("abc", "Hello world".bytes)
		def entry = store.getEntry("abc")
		def unknownEntry = store.getEntry("123")
		then:
		entry == "Hello world".bytes
		unknownEntry == null
	}

	def "pruneManyFiles"() {
		given:
		def cacheRules = new CachedFileStoreImpl.CacheRules(250, Duration.ofDays(7))
		def store = new CachedFileStoreImpl(root, BYTE_ARRAY_SERIALIZER, cacheRules)
		when:

		for (i in 0..<500) {
			def key = "test_" + i
			store.putEntry(key, "Hello world".bytes)
			// Higher files are older and should be removed.
			Files.setLastModifiedTime(root.resolve(key), FileTime.from(Instant.now().minusSeconds(i)))
		}

		store.prune()

		then:
		Files.exists(root.resolve("test_0"))
		Files.exists(root.resolve("test_100"))
		Files.notExists(root.resolve("test_300"))
	}

	def "pruneOldFiles"() {
		given:
		def cacheRules = new CachedFileStoreImpl.CacheRules(1000, Duration.ofSeconds(250))
		def store = new CachedFileStoreImpl(root, BYTE_ARRAY_SERIALIZER, cacheRules)
		when:

		for (i in 0..<500) {
			def key = "test_" + i
			store.putEntry(key, "Hello world".bytes)
			// Higher files are older and should be removed.
			Files.setLastModifiedTime(root.resolve(key), FileTime.from(Instant.now().minusSeconds(i)))
		}

		store.prune()

		then:
		Files.exists(root.resolve("test_0"))
		Files.exists(root.resolve("test_100"))
		Files.notExists(root.resolve("test_300"))
	}

	private static CachedFileStore.EntrySerializer<byte[]> BYTE_ARRAY_SERIALIZER = new CachedFileStore.EntrySerializer<byte[]>() {
		@Override
		byte[] read(Path path) throws IOException {
			return Files.readAllBytes(path)
		}

		@Override
		void write(byte[] entry, Path path) throws IOException {
			Files.write(path, entry)
		}
	}
}
