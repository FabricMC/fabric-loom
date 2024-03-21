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

package net.fabricmc.loom.decompilers.cache;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.decompilers.ClassLineNumbers;

// Serialised data for a class entry in the cache
// Uses the RIFF format, allows for appending the line numbers to the end of the file
// Stores the source code and line numbers for the class
public record CachedData(String className, String sources, @Nullable ClassLineNumbers.Entry lineNumbers) {
	public static final CachedFileStore.EntrySerializer<CachedData> SERIALIZER = new EntrySerializer();

	private static final String HEADER_ID = "LOOM";
	private static final String NAME_ID = "NAME";
	private static final String SOURCES_ID = "SRC ";
	private static final String LINE_NUMBERS_ID = "LNUM";

	private static final Logger LOGGER = LoggerFactory.getLogger(CachedData.class);

	public CachedData {
		Objects.requireNonNull(className, "className");
		Objects.requireNonNull(sources, "sources");

		if (lineNumbers != null) {
			if (!className.equals(lineNumbers.className())) {
				throw new IllegalArgumentException("Class name does not match line numbers class name");
			}
		}
	}

	public void write(FileChannel fileChannel) {
		try (var c = new RiffChunk(HEADER_ID, fileChannel)) {
			writeClassname(fileChannel);
			writeSource(fileChannel);

			if (lineNumbers != null) {
				writeLineNumbers(fileChannel);
			}
		} catch (IOException e) {
			throw new RuntimeException("Failed to write cached data", e);
		}
	}

	private void writeClassname(FileChannel fileChannel) throws IOException {
		try (var c = new RiffChunk(NAME_ID, fileChannel)) {
			fileChannel.write(ByteBuffer.wrap(className.getBytes(StandardCharsets.UTF_8)));
		}
	}

	private void writeSource(FileChannel fileChannel) throws IOException {
		try (var c = new RiffChunk(SOURCES_ID, fileChannel)) {
			fileChannel.write(ByteBuffer.wrap(sources.getBytes(StandardCharsets.UTF_8)));
		}
	}

	private void writeLineNumbers(FileChannel fileChannel) throws IOException {
		Objects.requireNonNull(lineNumbers);

		try (var c = new RiffChunk(LINE_NUMBERS_ID, fileChannel);
				StringWriter stringWriter = new StringWriter()) {
			lineNumbers.write(stringWriter);
			fileChannel.write(ByteBuffer.wrap(stringWriter.toString().getBytes(StandardCharsets.UTF_8)));
		}
	}

	public static CachedData read(InputStream inputStream) throws IOException {
		// Read and validate the RIFF header
		final String header = readHeader(inputStream);

		if (!header.equals(HEADER_ID)) {
			throw new IOException("Invalid RIFF header: " + header + ", expected " + HEADER_ID);
		}

		// Read the data length
		int length = readInt(inputStream);

		String className = null;
		String sources = null;
		ClassLineNumbers.Entry lineNumbers = null;

		while (inputStream.available() > 0) {
			String chunkHeader = readHeader(inputStream);
			int chunkLength = readInt(inputStream);
			byte[] chunkData = readBytes(inputStream, chunkLength);

			switch (chunkHeader) {
			case NAME_ID -> {
				if (className != null) {
					throw new IOException("Duplicate name chunk");
				}

				className = new String(chunkData, StandardCharsets.UTF_8);
			}
			case SOURCES_ID -> {
				if (sources != null) {
					throw new IOException("Duplicate sources chunk");
				}

				sources = new String(chunkData, StandardCharsets.UTF_8);
			}
			case LINE_NUMBERS_ID -> {
				if (lineNumbers != null) {
					throw new IOException("Duplicate line numbers chunk");
				}

				try (var br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(chunkData), StandardCharsets.UTF_8))) {
					ClassLineNumbers classLineNumbers = ClassLineNumbers.readMappings(br);

					if (classLineNumbers.lineMap().size() != 1) {
						throw new IOException("Expected exactly one class line numbers entry got " + classLineNumbers.lineMap().size() + " entries");
					}

					lineNumbers = classLineNumbers.lineMap().values().iterator().next();
				}
			}
			default -> {
				// Skip unknown chunk
				LOGGER.warn("Skipping unknown chunk: {} of size {}", chunkHeader, chunkLength);
				inputStream.skip(chunkLength);
			}
			}
		}

		if (sources == null) {
			throw new IOException("Missing sources");
		}

		return new CachedData(className, sources, lineNumbers);
	}

	private static String readHeader(InputStream inputStream) throws IOException {
		byte[] header = readBytes(inputStream, 4);
		return new String(header, StandardCharsets.US_ASCII);
	}

	private static int readInt(InputStream inputStream) throws IOException {
		byte[] bytes = readBytes(inputStream, 4);
		return ByteBuffer.wrap(bytes).getInt();
	}

	private static byte[] readBytes(InputStream inputStream, int length) throws IOException {
		byte[] bytes = new byte[length];

		int read = inputStream.read(bytes);

		if (read != length) {
			throw new IOException("Failed to read bytes expected " + length + " bytes but got " + read + " bytes");
		}

		return bytes;
	}

	static class EntrySerializer implements CachedFileStore.EntrySerializer<CachedData> {
		@Override
		public CachedData read(Path path) throws IOException {
			try (var inputStream = new BufferedInputStream(Files.newInputStream(path))) {
				return CachedData.read(inputStream);
			}
		}

		@Override
		public void write(CachedData entry, Path path) throws IOException {
			try (FileChannel fileChannel = FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
				entry.write(fileChannel);
			}
		}
	}
}
