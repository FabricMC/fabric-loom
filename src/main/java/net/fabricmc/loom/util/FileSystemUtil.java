/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2022 FabricMC
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.function.Supplier;

import net.fabricmc.tinyremapper.FileSystemReference;

public final class FileSystemUtil {
	public record Delegate(FileSystemReference reference) implements AutoCloseable, Supplier<FileSystem> {
		public Path getPath(String path, String... more) {
			return get().getPath(path, more);
		}

		public byte[] readAllBytes(String path) throws IOException {
			Path fsPath = getPath(path);

			if (Files.exists(fsPath)) {
				return Files.readAllBytes(fsPath);
			} else {
				throw new NoSuchFileException(fsPath.toString());
			}
		}

		public <T> T fromInputStream(IOFunction<InputStream, T> function, String path, String... more) throws IOException {
			try (InputStream inputStream = Files.newInputStream(getPath(path, more))) {
				return function.apply(inputStream);
			}
		}

		public String readString(String path) throws IOException {
			return new String(readAllBytes(path), StandardCharsets.UTF_8);
		}

		@Override
		public void close() throws IOException {
			reference.close();
		}

		@Override
		public FileSystem get() {
			return reference.getFs();
		}

		// TODO cleanup
		public FileSystem fs() {
			return get();
		}
	}

	private FileSystemUtil() {
	}

	public static Delegate getJarFileSystem(File file, boolean create) throws IOException {
		return new Delegate(FileSystemReference.openJar(file.toPath(), create));
	}

	public static Delegate getJarFileSystem(Path path, boolean create) throws IOException {
		return new Delegate(FileSystemReference.openJar(path, create));
	}

	public static Delegate getJarFileSystem(Path path) throws IOException {
		return new Delegate(FileSystemReference.openJar(path));
	}

	public static Delegate getJarFileSystem(URI uri, boolean create) throws IOException {
		return new Delegate(FileSystemReference.open(uri, create));
	}
}
