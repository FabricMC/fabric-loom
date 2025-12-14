/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2023 FabricMC
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

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.FileSystemUtil;
import net.fabricmc.loom.util.Lazy;
import net.fabricmc.loom.util.SnowmanClassVisitor;
import net.fabricmc.loom.util.SyntheticParameterClassVisitor;

public class MinecraftJarMerger implements AutoCloseable {
	private final MinecraftClassMerger classMerger = new MinecraftClassMerger();
	private final FileSystemUtil.Delegate inputClientFs, inputServerFs, outputFs;
	private final Path inputClient, inputServer;
	private final Map<String, Entry> entriesClient, entriesServer;
	private final Set<String> entriesAll;
	private boolean removeSnowmen = false;
	private boolean offsetSyntheticsParams = false;

	public MinecraftJarMerger(File inputClient, File inputServer, File output) throws IOException {
		if (output.exists()) {
			if (!output.delete()) {
				throw new IOException("Could not delete " + output.getName());
			}
		}

		Files.createDirectories(output.toPath().getParent());

		this.inputClient = (inputClientFs = FileSystemUtil.getJarFileSystem(inputClient, false)).get().getPath("/");
		this.inputServer = (inputServerFs = FileSystemUtil.getJarFileSystem(inputServer, false)).get().getPath("/");
		this.outputFs = FileSystemUtil.getJarFileSystem(output, true);

		this.entriesClient = new HashMap<>();
		this.entriesServer = new HashMap<>();
		this.entriesAll = new TreeSet<>();
	}

	public void enableSnowmanRemoval() {
		removeSnowmen = true;
	}

	public void enableSyntheticParamsOffset() {
		offsetSyntheticsParams = true;
	}

	@Override
	public void close() throws IOException {
		inputClientFs.close();
		inputServerFs.close();
		outputFs.close();
	}

	private void readToMap(Map<String, Entry> map, Path input) {
		try {
			Files.walkFileTree(input, new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult visitFile(Path path, BasicFileAttributes attr) throws IOException {
					if (attr.isDirectory()) {
						return FileVisitResult.CONTINUE;
					}

					if (!path.getFileName().toString().endsWith(".class")) {
						if (path.toString().equals("/META-INF/MANIFEST.MF")) {
							map.put("META-INF/MANIFEST.MF", new Entry(path, attr,
									"Manifest-Version: 1.0\nMain-Class: net.minecraft.client.Main\n".getBytes(StandardCharsets.UTF_8)));
						} else {
							if (path.toString().startsWith("/META-INF/")) {
								if (path.toString().endsWith(".SF") || path.toString().endsWith(".RSA")) {
									return FileVisitResult.CONTINUE;
								}
							}

							map.put(path.toString().substring(1), new Entry(path, attr));
						}

						return FileVisitResult.CONTINUE;
					}

					map.put(path.toString().substring(1), new Entry(path, attr, Lazy.of(() -> {
						try {
							return Files.readAllBytes(path);
						} catch (IOException e) {
							throw new UncheckedIOException("Failed to read " + path, e);
						}
					})));

					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to read", e);
		}
	}

	private void add(Entry entry) {
		try {
			Path outPath = outputFs.get().getPath(entry.path.toString());

			if (outPath.getParent() != null) {
				Files.createDirectories(outPath.getParent());
			}

			final byte[] data = entry.data();

			if (data != null) {
				Files.write(outPath, data, StandardOpenOption.CREATE_NEW);
			} else {
				Files.copy(entry.path, outPath);
			}

			Files.getFileAttributeView(outPath, BasicFileAttributeView.class)
					.setTimes(
							entry.metadata.creationTime(),
							entry.metadata.lastAccessTime(),
							entry.metadata.lastModifiedTime()
					);
		} catch (IOException ioe) {
			throw new UncheckedIOException("Failed to write " + entry.path, ioe);
		}
	}

	public void merge() throws IOException {
		try (ExecutorService service = Executors.newFixedThreadPool(2)) {
			service.submit(() -> readToMap(entriesClient, inputClient));
			service.submit(() -> readToMap(entriesServer, inputServer));
		}

		entriesAll.addAll(entriesClient.keySet());
		entriesAll.addAll(entriesServer.keySet());

		try (ExecutorService service = Executors.newWorkStealingPool()) {
			for (String entry : entriesAll) {
				processEntry(entry, service);
			}
		}
	}

	private void processEntry(String entry, Executor executor) {
		boolean isClass = entry.endsWith(".class");
		boolean isMinecraft = entriesClient.containsKey(entry) || entry.startsWith("net/minecraft") || !entry.contains("/");
		Result r = getResult(entry, isClass);

		if (isClass && !isMinecraft && "SERVER".equals(r.side())) {
			// Server bundles libraries, client doesn't - skip them
			return;
		}

		if (isMinecraft && isClass) {
			CompletableFuture.supplyAsync(() -> mergeClass(entry, r), executor)
					.thenAccept(this::add);
			return;
		}

		CompletableFuture.runAsync(() -> add(r.entry), executor);
	}

	private Result getResult(String entry, boolean isClass) {
		Entry result;
		String side = null;

		Entry clientEntry = entriesClient.get(entry);
		Entry serverEntry = entriesServer.get(entry);

		// Entry is common
		if (clientEntry != null && serverEntry != null) {
			byte[] clientData = clientEntry.data();
			byte[] serverData = serverEntry.data();

			if (Arrays.equals(clientData, serverData)) {
				result = clientEntry;
			} else {
				if (isClass) {
					Objects.requireNonNull(clientData);
					Objects.requireNonNull(serverData);
					result = new Entry(clientEntry.path, clientEntry.metadata, classMerger.merge(clientData, serverData));
				} else {
					// FIXME: More heuristics?
					result = clientEntry;
				}
			}
		} else if ((result = clientEntry) != null) {
			side = "CLIENT";
		} else if ((result = serverEntry) != null) {
			side = "SERVER";
		}

		return new Result(result, side);
	}

	private Entry mergeClass(String entry, Result result) {
		byte[] data = result.entry().data();
		Objects.requireNonNull(data, "Class data cannot be null");
		ClassReader reader = new ClassReader(data);
		ClassWriter writer = new ClassWriter(0);
		ClassVisitor visitor = writer;

		if (result.side() != null) {
			visitor = new MinecraftClassMerger.SidedClassVisitor(Constants.ASM_VERSION, visitor, result.side());
		}

		if (removeSnowmen) {
			visitor = new SnowmanClassVisitor(Constants.ASM_VERSION, visitor);
		}

		if (offsetSyntheticsParams && !entry.contains("/")) {
			visitor = new SyntheticParameterClassVisitor(Constants.ASM_VERSION, visitor);
		}

		if (visitor != writer) {
			reader.accept(visitor, 0);
			data = writer.toByteArray();
			return new Entry(result.entry().path, result.entry().metadata, data);
		}

		return result.entry();
	}

	// Data is null for non-class files, in this case the entry can be copied without uncompressing
	public record Entry(Path path, BasicFileAttributes metadata, @Nullable Supplier<byte[]> dataSupplier) {
		public Entry(Path path, BasicFileAttributes metadata, byte @Nullable [] data) {
			this(path, metadata, () -> data);
		}

		public Entry(Path path, BasicFileAttributes metadata) {
			this(path, metadata, (Supplier<byte[]>) null);
		}

		byte @Nullable [] data() {
			if (dataSupplier == null) {
				return null;
			}

			return dataSupplier.get();
		}
	}

	private record Result(Entry entry, @Nullable String side) {
	}
}
