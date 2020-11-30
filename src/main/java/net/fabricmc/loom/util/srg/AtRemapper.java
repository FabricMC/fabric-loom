/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016, 2017, 2018 FabricMC
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

package net.fabricmc.loom.util.srg;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.ImmutableMap;

import net.fabricmc.loom.util.function.CollectionUtil;
import net.fabricmc.mapping.tree.TinyTree;

/**
 * Remaps AT classes from SRG to Yarn.
 *
 * @author Juuz
 */
public final class AtRemapper {
	public static void remap(Path jar, TinyTree mappings) throws IOException {
		try (FileSystem fs = FileSystems.newFileSystem(URI.create("jar:" + jar.toUri()), ImmutableMap.of("create", false))) {
			Path atPath = fs.getPath("META-INF", "accesstransformer.cfg");

			if (Files.exists(atPath)) {
				List<String> lines = Files.readAllLines(atPath);
				List<String> output = new ArrayList<>(lines.size());

				for (int i = 0; i < lines.size(); i++) {
					String line = lines.get(i).trim();

					if (line.startsWith("#")) {
						output.add(i, line);
						continue;
					}

					String[] parts = line.split(" ");
					String name = parts[1].replace('.', '/');
					parts[1] = CollectionUtil.find(
							mappings.getClasses(),
							def -> def.getName("srg").equals(name)
					).map(def -> def.getName("named")).orElse(name).replace('/', '.');

					output.add(i, String.join(" ", parts));
				}

				if (!lines.equals(output)) {
					Files.write(atPath, output);
				}
			}
		}
	}
}
