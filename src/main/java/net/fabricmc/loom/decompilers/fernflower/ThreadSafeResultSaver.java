/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2019-2020 FabricMC
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

package net.fabricmc.loom.decompilers.fernflower;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;

import net.fabricmc.fernflower.api.IFabricResultSaver;

/**
 * Created by covers1624 on 18/02/19.
 */
public class ThreadSafeResultSaver implements IResultSaver, IFabricResultSaver {
	private final Supplier<File> output;
	private final Supplier<File> lineMapFile;

	public Map<String, ZipOutputStream> outputStreams = new HashMap<>();
	public Map<String, ExecutorService> saveExecutors = new HashMap<>();
	public PrintWriter lineMapWriter;

	public ThreadSafeResultSaver(Supplier<File> output, Supplier<File> lineMapFile) {
		this.output = output;
		this.lineMapFile = lineMapFile;
	}

	@Override
	public void createArchive(String path, String archiveName, Manifest manifest) {
		String key = path + "/" + archiveName;
		File file = output.get();

		try {
			FileOutputStream fos = new FileOutputStream(file);
			ZipOutputStream zos = manifest == null ? new ZipOutputStream(fos) : new JarOutputStream(fos, manifest);
			outputStreams.put(key, zos);
			saveExecutors.put(key, Executors.newSingleThreadExecutor());
		} catch (IOException e) {
			throw new RuntimeException("Unable to create archive: " + file, e);
		}

		if (lineMapFile.get() != null) {
			try {
				lineMapWriter = new PrintWriter(new FileWriter(lineMapFile.get()));
			} catch (IOException e) {
				throw new RuntimeException("Unable to create line mapping file: " + lineMapFile.get(), e);
			}
		}
	}

	@Override
	public void saveClassEntry(String path, String archiveName, String qualifiedName, String entryName, String content) {
		this.saveClassEntry(path, archiveName, qualifiedName, entryName, content, null);
	}

	@Override
	public void saveClassEntry(String path, String archiveName, String qualifiedName, String entryName, String content, int[] mapping) {
		String key = path + "/" + archiveName;
		ExecutorService executor = saveExecutors.get(key);
		executor.submit(() -> {
			ZipOutputStream zos = outputStreams.get(key);

			try {
				zos.putNextEntry(new ZipEntry(entryName));

				if (content != null) {
					zos.write(content.getBytes(StandardCharsets.UTF_8));
				}
			} catch (IOException e) {
				DecompilerContext.getLogger().writeMessage("Cannot write entry " + entryName, e);
			}

			if (mapping != null && lineMapWriter != null) {
				int maxLine = 0;
				int maxLineDest = 0;
				StringBuilder builder = new StringBuilder();

				for (int i = 0; i < mapping.length; i += 2) {
					maxLine = Math.max(maxLine, mapping[i]);
					maxLineDest = Math.max(maxLineDest, mapping[i + 1]);
					builder.append("\t").append(mapping[i]).append("\t").append(mapping[i + 1]).append("\n");
				}

				lineMapWriter.println(qualifiedName + "\t" + maxLine + "\t" + maxLineDest);
				lineMapWriter.println(builder.toString());
			}
		});
	}

	@Override
	public void closeArchive(String path, String archiveName) {
		String key = path + "/" + archiveName;
		ExecutorService executor = saveExecutors.get(key);
		Future<?> closeFuture = executor.submit(() -> {
			ZipOutputStream zos = outputStreams.get(key);

			try {
				zos.close();
			} catch (IOException e) {
				throw new RuntimeException("Unable to close zip. " + key, e);
			}
		});
		executor.shutdown();

		try {
			closeFuture.get();
		} catch (InterruptedException | ExecutionException e) {
			throw new RuntimeException(e);
		}

		outputStreams.remove(key);
		saveExecutors.remove(key);

		if (lineMapWriter != null) {
			lineMapWriter.flush();
			lineMapWriter.close();
		}
	}

	@Override
	public void saveFolder(String path) {
	}

	@Override
	public void copyFile(String source, String path, String entryName) {
	}

	@Override
	public void saveClassFile(String path, String qualifiedName, String entryName, String content, int[] mapping) {
	}

	@Override
	public void saveDirEntry(String path, String archiveName, String entryName) {
	}

	@Override
	public void copyEntry(String source, String path, String archiveName, String entry) {
	}
}
