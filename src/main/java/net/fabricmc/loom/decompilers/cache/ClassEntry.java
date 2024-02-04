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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.StringJoiner;

import net.fabricmc.loom.util.Checksum;

public record ClassEntry(String parentClass, List<String> innerClasses) {
	/**
	 * Copy the class and its inner classes to the target root.
	 * @param sourceRoot The root of the source jar
	 * @param targetRoot The root of the target jar
	 *
	 * @throws IOException If an error occurs while copying the files
	 */
	public void copyTo(Path sourceRoot, Path targetRoot) throws IOException {
		Path targetPath = targetRoot.resolve(parentClass);
		Files.createDirectories(targetPath.getParent());
		Files.copy(sourceRoot.resolve(parentClass), targetPath);

		for (String innerClass : innerClasses) {
			Files.copy(sourceRoot.resolve(innerClass), targetRoot.resolve(innerClass));
		}
	}

	/**
	 * Hash the class and its inner classes using sha256.
	 * @param root The root of the jar
	 * @return The hash of the class and its inner classes
	 *
	 * @throws IOException If an error occurs while hashing the files
	 */
	public String hash(Path root) throws IOException {
		StringJoiner joiner = new StringJoiner(",");

		joiner.add(Checksum.sha256Hex(Files.readAllBytes(root.resolve(parentClass))));

		for (String innerClass : innerClasses) {
			joiner.add(Checksum.sha256Hex(Files.readAllBytes(root.resolve(innerClass))));
		}

		return Checksum.sha256Hex(joiner.toString().getBytes());
	}

	public String sourcesFileName() {
		return parentClass.replace(".class", ".java");
	}
}
