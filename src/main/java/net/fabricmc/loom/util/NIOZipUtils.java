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

package net.fabricmc.loom.util;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.stitch.util.Pair;
import net.fabricmc.stitch.util.StitchUtil;

public class NIOZipUtils {
	public static boolean contains(Path zip, String path) {
		try (StitchUtil.FileSystemDelegate fs = StitchUtil.getJarFileSystem(zip.toFile(), false)) {
			Path fsPath = fs.get().getPath(path);

			return Files.exists(fsPath);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to check file from zip", e);
		}
	}

	public static void unpackAll(Path zip, Path output) {
		try (StitchUtil.FileSystemDelegate fs = StitchUtil.getJarFileSystem(new File(zip.toFile().getAbsolutePath()), false)) {
			for (Path fsPath : (Iterable<Path>) Files.walk(fs.get().getPath("/"))::iterator) {
				if (!Files.isRegularFile(fsPath)) continue;
				Path dstPath = output.resolve(fs.get().toString());
				Path dstPathParent = dstPath.getParent();
				if (dstPathParent != null) Files.createDirectories(dstPathParent);
				Files.copy(fsPath, dstPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
			}
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to unpack file from zip", e);
		}
	}

	public static byte @Nullable [] unpack(Path zip, String path) {
		try {
			return unpackStrict(zip, path);
		} catch (UncheckedIOException e) {
			if (e.getCause() instanceof NoSuchFileException) {
				return null;
			}

			throw e;
		}
	}

	public static byte[] unpackStrict(Path zip, String path) {
		try (StitchUtil.FileSystemDelegate fs = StitchUtil.getJarFileSystem(zip.toFile(), false)) {
			Path fsPath = fs.get().getPath(path);

			if (Files.exists(fsPath)) {
				return Files.readAllBytes(fsPath);
			} else {
				throw new NoSuchFileException(fsPath.toString());
			}
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to unpack file from zip", e);
		}
	}

	public static void pack(Path from, Path zip) {
		try {
			Files.deleteIfExists(zip);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to add file to zip", e);
		}

		if (!Files.isDirectory(from)) throw new IllegalArgumentException(from + " is not a directory!");

		try (StitchUtil.FileSystemDelegate fs = StitchUtil.getJarFileSystem(zip.toFile(), true)) {
			for (Path fromPath : (Iterable<Path>) Files.walk(from)::iterator) {
				if (!Files.isRegularFile(fromPath)) continue;
				Path fsPath = fs.get().getPath(from.relativize(fromPath).toString());
				Path fsPathParent = fsPath.getParent();
				if (fsPathParent != null) Files.createDirectories(fsPathParent);
				System.out.println(fromPath + " " + fsPath);
				Files.copy(fromPath, fsPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
			}
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to pack file to zip", e);
		}
	}

	public static void add(Path zip, String path, byte[] bytes) {
		add(zip, Collections.singleton(Pair.of(path, bytes)));
	}

	public static void add(Path zip, Iterable<Pair<String, byte[]>> files) {
		try (StitchUtil.FileSystemDelegate fs = StitchUtil.getJarFileSystem(zip.toFile(), true)) {
			for (Pair<String, byte[]> pair : files) {
				Path fsPath = fs.get().getPath(pair.getLeft());
				Path fsPathParent = fsPath.getParent();
				if (fsPathParent != null) Files.createDirectories(fsPathParent);
				Files.write(fsPath, pair.getRight(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			}
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to add file to zip", e);
		}
	}

	public static boolean replace(Path zip, String path, byte[] bytes) {
		try {
			replaceStrict(zip, path, bytes);
			return true;
		} catch (UncheckedIOException e) {
			if (e.getCause() instanceof NoSuchFileException) {
				return false;
			}

			throw e;
		}
	}

	public static void replaceStrict(Path zip, String path, byte[] bytes) {
		try (StitchUtil.FileSystemDelegate fs = StitchUtil.getJarFileSystem(zip.toFile(), true)) {
			Path fsPath = fs.get().getPath(path);

			if (Files.exists(fsPath)) {
				Files.write(fsPath, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			} else {
				throw new NoSuchFileException(fsPath.toString());
			}
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to replace file in zip", e);
		}
	}

	public static boolean transformString(Path zip, Collection<Pair<String, UnsafeUnaryOperator<String>>> transforms) {
		return transformString(zip, transforms.stream());
	}

	public static boolean transformString(Path zip, Stream<Pair<String, UnsafeUnaryOperator<String>>> transforms) {
		return transformString(zip, transforms
				.collect(Collectors.groupingBy(Pair::getLeft,
						Collectors.mapping(Pair::getRight,
								Collectors.reducing(null, (o, o2) -> o)))));
	}

	public static boolean transformString(Path zip, Map<String, UnsafeUnaryOperator<String>> transforms) {
		return transformMapped(zip, transforms, bytes -> new String(bytes, StandardCharsets.UTF_8), s -> s.getBytes(StandardCharsets.UTF_8));
	}

	public static <T> boolean transformJson(Class<T> typeOfT, Path zip, Collection<Pair<String, UnsafeUnaryOperator<T>>> transforms) {
		return transformJson(typeOfT, zip, transforms.stream());
	}

	public static <T> boolean transformJson(Class<T> typeOfT, Path zip, Stream<Pair<String, UnsafeUnaryOperator<T>>> transforms) {
		return transformJson(typeOfT, zip, transforms
				.collect(Collectors.groupingBy(Pair::getLeft,
						Collectors.mapping(Pair::getRight,
								Collectors.reducing(null, (o, o2) -> o)))));
	}

	public static <T> boolean transformJson(Class<T> typeOfT, Path zip, Map<String, UnsafeUnaryOperator<T>> transforms) {
		return transformMapped(zip, transforms, bytes -> LoomGradlePlugin.GSON.fromJson(new InputStreamReader(new ByteArrayInputStream(bytes)), typeOfT),
				s -> LoomGradlePlugin.GSON.toJson(s, typeOfT).getBytes(StandardCharsets.UTF_8));
	}

	public static boolean transform(Path zip, Collection<Pair<String, UnsafeUnaryOperator<byte[]>>> transforms) {
		return transform(zip, transforms.stream());
	}

	public static boolean transform(Path zip, Stream<Pair<String, UnsafeUnaryOperator<byte[]>>> transforms) {
		return transform(zip, transforms
				.collect(Collectors.groupingBy(Pair::getLeft,
						Collectors.mapping(Pair::getRight,
								Collectors.reducing(null, (o, o2) -> o)))));
	}

	public static <T> boolean transformMapped(Path zip, Map<String, UnsafeUnaryOperator<T>> transforms, Function<byte[], T> deserializer, Function<T, byte[]> serializer) {
		Map<String, UnsafeUnaryOperator<byte[]>> newTransforms = new HashMap<>();

		for (Map.Entry<String, UnsafeUnaryOperator<T>> entry : transforms.entrySet()) {
			if (entry.getValue() != null) {
				newTransforms.put(entry.getKey(), bytes -> {
					return serializer.apply(entry.getValue().apply(deserializer.apply(bytes)));
				});
			}
		}

		return transform(zip, newTransforms);
	}

	public static boolean transform(Path zip, Map<String, UnsafeUnaryOperator<byte[]>> transforms) {
		int replacedCount = 0;

		try (StitchUtil.FileSystemDelegate fs = StitchUtil.getJarFileSystem(zip.toFile(), false)) {
			for (Map.Entry<String, UnsafeUnaryOperator<byte[]>> entry : transforms.entrySet()) {
				Path fsPath = fs.get().getPath(entry.getKey());

				if (Files.exists(fsPath) && entry.getValue() != null) {
					Files.write(fsPath, entry.getValue().apply(Files.readAllBytes(fsPath)), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
					replacedCount++;
				}
			}
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to transform file in zip", e);
		}

		return replacedCount > 0 || transforms.isEmpty();
	}

	@FunctionalInterface
	public interface UnsafeUnaryOperator<T> {
		T apply(T arg) throws IOException;
	}

	@FunctionalInterface
	public interface UnsafeFunction<T, R> {
		R apply(T arg) throws IOException;
	}
}
