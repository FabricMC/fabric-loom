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

import net.fabricmc.loom.util.function.CollectionUtil;
import net.fabricmc.mapping.tree.TinyTree;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.logging.Logger;
import org.zeroturnaround.zip.ZipUtil;
import org.zeroturnaround.zip.transform.StringZipEntryTransformer;
import org.zeroturnaround.zip.transform.ZipEntryTransformerEntry;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;

/**
 * Remaps AT classes from SRG to Yarn.
 *
 * @author Juuz
 */
public final class AtRemapper {
	public static void remap(Logger logger, Path jar, TinyTree mappings) throws IOException {
		ZipUtil.transformEntries(jar.toFile(), new ZipEntryTransformerEntry[]{(new ZipEntryTransformerEntry("META-INF/accesstransformer.cfg", new StringZipEntryTransformer() {
			@Override
			protected String transform(ZipEntry zipEntry, String input) {
				String[] lines = input.split("\n");
				List<String> output = new ArrayList<>(lines.length);
				
				for (int i = 0; i < lines.length; i++) {
					String line = lines[i].trim();
					
					if (line.startsWith("#") || StringUtils.isBlank(line)) {
						output.add(i, line);
						continue;
					}
					
					String[] parts = line.split(" ");
					if (parts.length < 2) {
						logger.warn("Invalid AT Line: " + line);
						output.add(i, line);
						continue;
					}
					String name = parts[1].replace('.', '/');
					parts[1] = CollectionUtil.find(
							mappings.getClasses(),
							def -> def.getName("srg").equals(name)
					).map(def -> def.getName("named")).orElse(name).replace('/', '.');
					
					output.add(i, String.join(" ", parts));
				}
				
				return String.join("\n", output);
			}
		}))});
	}
}
