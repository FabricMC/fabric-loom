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
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.function.Supplier;

import net.fabricmc.tinyremapper.FileSystemReference;

public final class FileSystemUtil {
	public record Delegate(FileSystemReference reference, URI uri) implements AutoCloseable, Supplier<FileSystem> {
		public Path getPath(String path, String... more) {
			return get().getPath(path, more);
		}

		public Path getRoot() {
			return get().getPath("/");
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
			try {
				reference.close();
			} catch (IOException e) {
				// An IOException can only ever be thrown by the underlying FileSystem.close() call in tiny remapper
				// This means that this reference was the last open
				try {
					// We would then almost always expect this to throw a FileSystemNotFoundException
					FileSystem fileSystem = FileSystems.getFileSystem(uri);

					if (fileSystem.isOpen()) {
						// Or the unlikely chance that another thread opened a new reference
						throw e;
					}

					// However if we end up here, the closed FileSystem was not removed from ZipFileSystemProvider.filesystems
					// This leaves us in a broken state, preventing this JVM from ever being able to open a zip at this path.
					// See: https://bugs.openjdk.org/browse/JDK-8291712
					throw new UnrecoverableZipException(e.getMessage(), e);
				} catch (FileSystemNotFoundException ignored) {
					// This the "happy" case, where the zip FS failed to close but was
				}

				// Throw the normal exception, we can recover from this
				throw e;
			}
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
		return new Delegate(FileSystemReference.openJar(file.toPath(), create), toJarUri(file.toPath()));
	}

	public static Delegate getJarFileSystem(Path path, boolean create) throws IOException {
		return new Delegate(FileSystemReference.openJar(path, create), toJarUri(path));
	}

	public static Delegate getJarFileSystem(Path path) throws IOException {
		return new Delegate(FileSystemReference.openJar(path), toJarUri(path));
	}

	public static Delegate getJarFileSystem(URI uri, boolean create) throws IOException {
		return new Delegate(FileSystemReference.open(uri, create), uri);
	}

	private static URI toJarUri(Path path) {
		URI uri = path.toUri();

		try {
			return new URI("jar:" + uri.getScheme(), uri.getHost(), uri.getPath(), uri.getFragment());
		} catch (URISyntaxException e) {
			throw new RuntimeException("can't convert path "+path+" to uri", e);
		}
	}

	public static class UnrecoverableZipException extends RuntimeException {
		public UnrecoverableZipException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
