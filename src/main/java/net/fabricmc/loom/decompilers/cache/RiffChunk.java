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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;

/**
 * Write a RIFF chunk to a file channel
 *
 * <p>Works by writing the chunk header and then reserving space for the chunk size.
 * The chunk size is then written after the chunk data has been written.
 */
public class RiffChunk implements AutoCloseable {
	private final long position;
	private final FileChannel fileChannel;

	public RiffChunk(String id, FileChannel fileChannel) throws IOException {
		if (id.length() != 4) {
			throw new IllegalArgumentException("ID must be 4 characters long");
		}

		// Write the chunk header and reserve space for the chunk size
		fileChannel.write(ByteBuffer.wrap(id.getBytes(StandardCharsets.US_ASCII)));
		this.position = fileChannel.position();
		fileChannel.write(ByteBuffer.allocate(4));

		// Store the position and file channel for later use
		this.fileChannel = fileChannel;
	}

	@Override
	public void close() throws IOException {
		long endPosition = fileChannel.position();
		long chunkSize = endPosition - position - 4;

		if (chunkSize > Integer.MAX_VALUE) {
			throw new IOException("Chunk size is too large");
		}

		fileChannel.position(position);
		fileChannel.write(ByteBuffer.allocate(Integer.BYTES).putInt((int) (chunkSize)).flip());
		fileChannel.position(endPosition);
	}
}
