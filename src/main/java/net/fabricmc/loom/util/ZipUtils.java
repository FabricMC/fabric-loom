/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021-2022 FabricMC
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
import java.io.DataInputStream;
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
import java.util.Iterator;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

import net.fabricmc.loom.LoomGradlePlugin;

public class ZipUtils {
	public static boolean isZip(Path zip) throws IOException {
		if (Files.notExists(zip)) {
			throw new NoSuchFileException("Cannot check if '" + zip + "' is a zip because it doesn't exist!");
		}

		if (Files.isRegularFile(zip)) {
			try (DataInputStream in = new DataInputStream(Files.newInputStream(zip))) {
				int header = in.readInt();
				// See https://en.wikipedia.org/wiki/List_of_file_signatures
				return header == 0x504B0304 || header == 0x504B0506 || header == 0x504B0708;
			}
		} else {
			return false;
		}
	}

	public static boolean contains(Path zip, String path) {
		try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(zip, false)) {
			Path fsPath = fs.get().getPath(path);

			return Files.exists(fsPath);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to check file from zip", e);
		}
	}

	public static void unpackAll(Path zip, Path output) throws IOException {
		try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(zip, false);
				Stream<Path> walk = Files.walk(fs.getRoot())) {
			Iterator<Path> iterator = walk.iterator();

			while (iterator.hasNext()) {
				Path fsPath = iterator.next();
				if (!Files.isRegularFile(fsPath)) continue;
				Path dstPath = output.resolve(fs.getRoot().relativize(fsPath).toString());
				Path dstPathParent = dstPath.getParent();
				if (dstPathParent != null) Files.createDirectories(dstPathParent);
				Files.copy(fsPath, dstPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
			}
		}
	}

	public static byte @Nullable [] unpackNullable(Path zip, String path) throws IOException {
		try {
			return unpack(zip, path);
		} catch (NoSuchFileException e) {
			return null;
		}
	}

	public static byte[] unpack(Path zip, String path) throws IOException {
		try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(zip, false)) {
			return fs.readAllBytes(path);
		}
	}

	public static <T> T unpackGson(Path zip, String path, Class<T> clazz) throws IOException {
		final byte[] bytes = unpack(zip, path);
		return LoomGradlePlugin.GSON.fromJson(new String(bytes, StandardCharsets.UTF_8), clazz);
	}

	@Nullable
	public static <T> T unpackGsonNullable(Path zip, String path, Class<T> clazz) throws IOException {
		try {
			return unpackGson(zip, path, clazz);
		} catch (NoSuchFileException e) {
			return null;
		}
	}

	public static <T> T unpackJson(Path zip, String path, Class<T> clazz) throws IOException {
		final byte[] bytes = unpack(zip, path);
		return LoomGradlePlugin.GSON.fromJson(new String(bytes, StandardCharsets.UTF_8), clazz);
	}

	public static void pack(Path from, Path zip) throws IOException {
		Files.deleteIfExists(zip);

		if (!Files.isDirectory(from)) throw new IllegalArgumentException(from + " is not a directory!");

		int count = 0;

		try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(zip, true);
				Stream<Path> walk = Files.walk(from)) {
			Iterator<Path> iterator = walk.iterator();

			while (iterator.hasNext()) {
				Path fromPath = iterator.next();
				if (!Files.isRegularFile(fromPath)) continue;
				Path fsPath = fs.get().getPath(from.relativize(fromPath).toString());
				Path fsPathParent = fsPath.getParent();
				if (fsPathParent != null) Files.createDirectories(fsPathParent);
				Files.copy(fromPath, fsPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
				count++;
			}
		}

		if (count == 0) {
			throw new IOException("Noting packed into %s from %s".formatted(zip, from));
		}
	}

	public static void add(Path zip, String path, String str) throws IOException {
		add(zip, path, str.getBytes(StandardCharsets.UTF_8));
	}

	public static void add(Path zip, String path, byte[] bytes) throws IOException {
		add(zip, Collections.singleton(new Pair<>(path, bytes)));
	}

	public static void add(Path zip, Iterable<Pair<String, byte[]>> files) throws IOException {
		try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(zip, true)) {
			for (Pair<String, byte[]> pair : files) {
				Path fsPath = fs.get().getPath(pair.left());
				Path fsPathParent = fsPath.getParent();
				if (fsPathParent != null) Files.createDirectories(fsPathParent);
				Files.write(fsPath, pair.right(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			}
		}
	}

	public static void replace(Path zip, String path, byte[] bytes) throws IOException {
		try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(zip, true)) {
			Path fsPath = fs.get().getPath(path);

			if (Files.exists(fsPath)) {
				Files.write(fsPath, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			} else {
				throw new NoSuchFileException(fsPath.toString());
			}
		}
	}

	public static int transformString(Path zip, Collection<Pair<String, UnsafeUnaryOperator<String>>> transforms) throws IOException {
		return transformString(zip, transforms.stream());
	}

	public static int transformString(Path zip, Stream<Pair<String, UnsafeUnaryOperator<String>>> transforms) throws IOException {
		return transformString(zip, collectTransformersStream(transforms));
	}

	public static int transformString(Path zip, Map<String, UnsafeUnaryOperator<String>> transforms) throws IOException {
		return transformMapped(zip, transforms, bytes -> new String(bytes, StandardCharsets.UTF_8), s -> s.getBytes(StandardCharsets.UTF_8));
	}

	public static <T> int transformJson(Class<T> typeOfT, Path zip, Collection<Pair<String, UnsafeUnaryOperator<T>>> transforms) throws IOException {
		return transformJson(typeOfT, zip, transforms.stream());
	}

	public static <T> int transformJson(Class<T> typeOfT, Path zip, Stream<Pair<String, UnsafeUnaryOperator<T>>> transforms) throws IOException {
		return transformJson(typeOfT, zip, collectTransformersStream(transforms));
	}

	public static <T> int transformJson(Class<T> typeOfT, Path zip, Map<String, UnsafeUnaryOperator<T>> transforms) throws IOException {
		return transformMapped(zip, transforms, bytes -> LoomGradlePlugin.GSON.fromJson(new InputStreamReader(new ByteArrayInputStream(bytes)), typeOfT),
				s -> LoomGradlePlugin.GSON.toJson(s, typeOfT).getBytes(StandardCharsets.UTF_8));
	}

	public static <T> void transformJson(Class<T> typeOfT, Path zip, String path, UnsafeUnaryOperator<T> transformer) throws IOException {
		int transformed = transformJson(typeOfT, zip, Map.of(path, transformer));

		if (transformed != 1) {
			throw new IOException("Failed to transform " + path + " in " + zip);
		}
	}

	public static int transform(Path zip, Collection<Pair<String, UnsafeUnaryOperator<byte[]>>> transforms) throws IOException {
		return transform(zip, transforms.stream());
	}

	public static int transform(Path zip, Stream<Pair<String, UnsafeUnaryOperator<byte[]>>> transforms) throws IOException {
		return transform(zip, collectTransformersStream(transforms));
	}

	public static <T> int transformMapped(Path zip, Map<String, UnsafeUnaryOperator<T>> transforms, Function<byte[], T> deserializer, Function<T, byte[]> serializer) throws IOException {
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

	public static int transform(Path zip, Map<String, UnsafeUnaryOperator<byte[]>> transforms) throws IOException {
		int replacedCount = 0;

		try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(zip, false)) {
			for (Map.Entry<String, UnsafeUnaryOperator<byte[]>> entry : transforms.entrySet()) {
				Path fsPath = fs.get().getPath(entry.getKey());

				if (Files.exists(fsPath) && entry.getValue() != null) {
					Files.write(fsPath, entry.getValue().apply(Files.readAllBytes(fsPath)), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
					replacedCount++;
				}
			}
		}

		return replacedCount;
	}

	@FunctionalInterface
	public interface UnsafeUnaryOperator<T> {
		T apply(T arg) throws IOException;
	}

	public interface AsmClassOperator extends UnsafeUnaryOperator<byte[]> {
		ClassVisitor visit(ClassVisitor classVisitor);

		@Override
		default byte[] apply(byte[] arg) throws IOException {
			final ClassReader reader = new ClassReader(arg);
			final ClassWriter writer = new ClassWriter(0);

			reader.accept(visit(writer), 0);

			return writer.toByteArray();
		}
	}

	private static <T> Map<String, UnsafeUnaryOperator<T>> collectTransformersStream(Stream<Pair<String, UnsafeUnaryOperator<T>>> transforms) {
		Map<String, UnsafeUnaryOperator<T>> map = new HashMap<>();
		Iterator<Pair<String, UnsafeUnaryOperator<T>>> iterator = transforms.iterator();

		while (iterator.hasNext()) {
			Pair<String, UnsafeUnaryOperator<T>> next = iterator.next();
			map.put(next.left(), next.right());
		}

		return map;
	}
}
