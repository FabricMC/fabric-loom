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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.FileSystemUtil;
import net.fabricmc.loom.util.SnowmanClassVisitor;
import net.fabricmc.loom.util.SyntheticParameterClassVisitor;

public class MinecraftJarMerger implements AutoCloseable {
	public static class Entry {
		public final Path path;
		public final BasicFileAttributes metadata;
		public final byte[] data;

		public Entry(Path path, BasicFileAttributes metadata, byte[] data) {
			this.path = path;
			this.metadata = metadata;
			this.data = data;
		}
	}

	private static final MinecraftClassMerger CLASS_MERGER = new MinecraftClassMerger();
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

							map.put(path.toString().substring(1), new Entry(path, attr, null));
						}

						return FileVisitResult.CONTINUE;
					}

					byte[] output = Files.readAllBytes(path);
					map.put(path.toString().substring(1), new Entry(path, attr, output));
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void add(Entry entry) throws IOException {
		Path outPath = outputFs.get().getPath(entry.path.toString());

		if (outPath.getParent() != null) {
			Files.createDirectories(outPath.getParent());
		}

		if (entry.data != null) {
			Files.write(outPath, entry.data, StandardOpenOption.CREATE_NEW);
		} else {
			Files.copy(entry.path, outPath);
		}

		Files.getFileAttributeView(outPath, BasicFileAttributeView.class)
				.setTimes(
						entry.metadata.creationTime(),
						entry.metadata.lastAccessTime(),
						entry.metadata.lastModifiedTime()
				);
	}

	public void merge() throws IOException {
		ExecutorService service = Executors.newFixedThreadPool(2);
		service.submit(() -> readToMap(entriesClient, inputClient));
		service.submit(() -> readToMap(entriesServer, inputServer));
		service.shutdown();

		try {
			service.awaitTermination(1, TimeUnit.HOURS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		entriesAll.addAll(entriesClient.keySet());
		entriesAll.addAll(entriesServer.keySet());

		List<Entry> entries = entriesAll.parallelStream().map((entry) -> {
			boolean isClass = entry.endsWith(".class");
			boolean isMinecraft = entriesClient.containsKey(entry) || entry.startsWith("net/minecraft") || !entry.contains("/");
			Entry result;
			String side = null;

			Entry entry1 = entriesClient.get(entry);
			Entry entry2 = entriesServer.get(entry);

			if (entry1 != null && entry2 != null) {
				if (Arrays.equals(entry1.data, entry2.data)) {
					result = entry1;
				} else {
					if (isClass) {
						result = new Entry(entry1.path, entry1.metadata, CLASS_MERGER.merge(entry1.data, entry2.data));
					} else {
						// FIXME: More heuristics?
						result = entry1;
					}
				}
			} else if ((result = entry1) != null) {
				side = "CLIENT";
			} else if ((result = entry2) != null) {
				side = "SERVER";
			}

			if (isClass && !isMinecraft && "SERVER".equals(side)) {
				// Server bundles libraries, client doesn't - skip them
				return null;
			}

			if (result != null) {
				if (isMinecraft && isClass) {
					byte[] data = result.data;
					ClassReader reader = new ClassReader(data);
					ClassWriter writer = new ClassWriter(0);
					ClassVisitor visitor = writer;

					if (side != null) {
						visitor = new MinecraftClassMerger.SidedClassVisitor(Constants.ASM_VERSION, visitor, side);
					}

					if (removeSnowmen) {
						visitor = new SnowmanClassVisitor(Constants.ASM_VERSION, visitor);
					}

					if (offsetSyntheticsParams) {
						visitor = new SyntheticParameterClassVisitor(Constants.ASM_VERSION, visitor);
					}

					if (visitor != writer) {
						reader.accept(visitor, 0);
						data = writer.toByteArray();
						result = new Entry(result.path, result.metadata, data);
					}
				}

				return result;
			} else {
				return null;
			}
		}).filter(Objects::nonNull).toList();

		for (Entry e : entries) {
			add(e);
		}
	}
}
