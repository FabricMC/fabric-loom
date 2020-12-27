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
import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
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
					
					String[] parts = line.split("\\s+");
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
					if (parts.length >= 3) {
						if (parts[2].contains("(")) {
							parts[2] = parts[2].substring(0, parts[2].indexOf('(')) + remapDescriptor(parts[2].substring(parts[2].indexOf('(')), s -> {
								return CollectionUtil.find(
										mappings.getClasses(),
										def -> def.getName("srg").equals(s)
								).map(def -> def.getName("named")).orElse(s);
							});
						}
					}
					
					output.add(i, String.join(" ", parts));
				}
				
				return String.join("\n", output);
			}
		}))});
	}
	
	private static String remapDescriptor(String original, UnaryOperator<String> classMappings) {
		try {
			StringReader reader = new StringReader(original);
			StringBuilder result = new StringBuilder();
			boolean insideClassName = false;
			StringBuilder className = new StringBuilder();
			while (true) {
				int c = reader.read();
				if (c == -1) {
					break;
				}
				if ((char) c == ';') {
					insideClassName = false;
					result.append(classMappings.apply(className.toString()));
				}
				if (insideClassName) {
					className.append((char) c);
				} else {
					result.append((char) c);
				}
				if (!insideClassName && (char) c == 'L') {
					insideClassName = true;
					className.setLength(0);
				}
			}
			return result.toString();
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}
}
