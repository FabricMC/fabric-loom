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

package net.fabricmc.loom.decompilers.jadx;

import java.io.IOException;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

public class JarEntryWriter {
	private final Set<String> addedDirectories = new HashSet<>();
	private final JarOutputStream outputStream;

	JarEntryWriter(JarOutputStream outputStream) {
		this.outputStream = outputStream;
	}
	
	synchronized void write(String filename, byte[] data) throws IOException {
		String[] path = filename.split("/");
		String pathPart = "";

		for (int i = 0; i < path.length - 1; i++) {
			pathPart += path[i] + "/";

			if (addedDirectories.add(pathPart)) {
				JarEntry entry = new JarEntry(pathPart);
				entry.setTime(new Date().getTime());

				try {
					outputStream.putNextEntry(entry);
					outputStream.closeEntry();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		}

		JarEntry entry = new JarEntry(filename);
		entry.setTime(new Date().getTime());
		entry.setSize(data.length);

		outputStream.putNextEntry(entry);
		outputStream.write(data);
		outputStream.closeEntry();
	}
}
