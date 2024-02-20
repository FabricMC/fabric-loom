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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.decompilers.ClassLineNumbers;

// Serialised data for a class entry in the cache
// Uses the RIFF format, allows for appending the line numbers to the end of the file
// Stores the source code and line numbers for the class
public record CachedData(String sources, @Nullable ClassLineNumbers.Entry lineNumbers) {
	private static final String HEADER_ID = "LOOM";
	private static final String SOURCES_ID = "SRC ";
	private static final String LINE_NUMBERS_ID = "LNUM";

	private static final Logger LOGGER = LoggerFactory.getLogger(CachedData.class);

	public void write(FileChannel fileChannel) {
		try (var c = new RiffChunk(HEADER_ID, fileChannel)) {
			writeSource(fileChannel);

			if (lineNumbers != null) {
				writeLineNumbers(fileChannel);
			}
		} catch (IOException e) {
			throw new RuntimeException("Failed to write cached data", e);
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

		String sources = null;
		ClassLineNumbers.Entry lineNumbers = null;

		while (inputStream.available() > 0) {
			String chunkHeader = readHeader(inputStream);
			int chunkLength = readInt(inputStream);
			byte[] chunkData = new byte[chunkLength];

			if (inputStream.read(chunkData) != chunkLength) {
				throw new IOException("Failed to read chunk data");
			}

			switch (chunkHeader) {
			case SOURCES_ID -> {
				sources = new String(chunkData, StandardCharsets.UTF_8);
			}
			case LINE_NUMBERS_ID -> {
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

		return new CachedData(sources, lineNumbers);
	}

	private static String readHeader(InputStream inputStream) throws IOException {
		byte[] header = new byte[4];

		if (inputStream.read(header) != 4) {
			throw new IOException("Failed to read header");
		}

		return new String(header, StandardCharsets.US_ASCII);
	}

	private static int readInt(InputStream inputStream) throws IOException {
		byte[] bytes = new byte[4];

		if (inputStream.read(bytes) != 4) {
			throw new IOException("Failed to read int");
		}

		return ByteBuffer.wrap(bytes).getInt();
	}
}
