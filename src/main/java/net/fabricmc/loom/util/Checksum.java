/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2020 FabricMC
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
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import com.google.common.io.ByteSource;
import com.google.common.io.Files;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

public class Checksum {
	private static final Logger log = Logging.getLogger(Checksum.class);

	public static boolean equals(File file, String checksum) {
		if (file == null || !file.exists()) {
			return false;
		}

		try {
			HashCode hash = Files.asByteSource(file).hash(Hashing.sha1());
			log.debug("Checksum check: '" + hash.toString() + "' == '" + checksum + "'?");
			return hash.toString().equals(checksum);
		} catch (IOException e) {
			e.printStackTrace();
		}

		return false;
	}

	public static byte[] sha256(File file) {
		try {
			HashCode hash = Files.asByteSource(file).hash(Hashing.sha256());
			return hash.asBytes();
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to get file hash", e);
		}
	}

	public static String sha256Hex(byte[] input) throws IOException {
		HashCode hash = ByteSource.wrap(input).hash(Hashing.sha256());
		return Checksum.toHex(hash.asBytes());
	}

	public static String sha1Hex(Path path) throws IOException {
		HashCode hash = Files.asByteSource(path.toFile()).hash(Hashing.sha1());
		return toHex(hash.asBytes());
	}

	public static String sha1Hex(byte[] input) {
		try {
			HashCode hash = ByteSource.wrap(input).hash(Hashing.sha1());
			return toHex(hash.asBytes());
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to hash", e);
		}
	}

	public static String truncatedSha256(File file) {
		try {
			HashCode hash = Files.asByteSource(file).hash(Hashing.sha256());
			return hash.toString().substring(0, 12);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to get file hash of " + file, e);
		}
	}

	public static String toHex(byte[] bytes) {
		return BaseEncoding.base16().lowerCase().encode(bytes);
	}

	public static String projectHash(Project project) {
		String str = project.getProjectDir().getAbsolutePath() + ":" + project.getPath();
		String hex = sha1Hex(str.getBytes(StandardCharsets.UTF_8));
		return hex.substring(hex.length() - 16);
	}
}
