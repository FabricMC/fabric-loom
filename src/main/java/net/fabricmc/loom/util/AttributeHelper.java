/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022 FabricMC
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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.util.Optional;

public final class AttributeHelper {
	private AttributeHelper() {
	}

	public static Optional<String> readAttribute(Path path, String key) throws IOException {
		final UserDefinedFileAttributeView attributeView = Files.getFileAttributeView(path, UserDefinedFileAttributeView.class);

		if (!attributeView.list().contains(key)) {
			return Optional.empty();
		}

		final ByteBuffer buffer = ByteBuffer.allocate(attributeView.size(key));
		attributeView.read(key, buffer);
		buffer.flip();
		final String value = StandardCharsets.UTF_8.decode(buffer).toString();
		return Optional.of(value);
	}

	public static void writeAttribute(Path path, String key, String value) throws IOException {
		// TODO may need to fallback to creating a separate file if this isnt supported.
		final UserDefinedFileAttributeView attributeView = Files.getFileAttributeView(path, UserDefinedFileAttributeView.class);
		final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		final ByteBuffer buffer = ByteBuffer.wrap(bytes);
		final int written = attributeView.write(key, buffer);
		assert written == bytes.length;
	}
}
