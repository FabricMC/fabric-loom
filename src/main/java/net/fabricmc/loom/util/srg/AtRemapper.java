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

import static me.shedaniel.architectury.refmapremapper.utils.DescriptorRemapper.remapDescriptor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.zip.ZipEntry;

import me.shedaniel.architectury.refmapremapper.remapper.SimpleReferenceRemapper;
import org.apache.logging.log4j.util.Strings;
import org.gradle.api.logging.Logger;
import org.jetbrains.annotations.Nullable;
import org.zeroturnaround.zip.ZipUtil;
import org.zeroturnaround.zip.transform.StringZipEntryTransformer;

import net.fabricmc.mapping.tree.TinyTree;

/**
 * Remaps AT contents.
 *
 * @author Juuz
 */
public final class AtRemapper {
	public static void remapSrgToNamed(Logger logger, Path jar, TinyTree mappings) throws IOException {
		ZipUtil.transformEntry(jar.toFile(), "META-INF/accesstransformer.cfg", new StringZipEntryTransformer() {
			@Override
			protected String transform(ZipEntry zipEntry, String input) {
				return remapAt(logger, input, new SimpleReferenceRemapper.Remapper() {
					@Override
					@Nullable
					public String mapClass(String value) {
						return mappings.getClasses().stream()
								.filter(classDef -> Objects.equals(classDef.getName("srg"), value))
								.findFirst()
								.map(classDef -> classDef.getName("named"))
								.orElse(null);
					}

					@Override
					@Nullable
					public String mapMethod(@Nullable String className, String methodName, String methodDescriptor) {
						return null;
					}

					@Override
					@Nullable
					public String mapField(@Nullable String className, String fieldName, String fieldDescriptor) {
						return null;
					}
				});
			}
		});
	}

	public static String remapAt(Logger logger, String sourceAt, SimpleReferenceRemapper.Remapper remapper) {
		String[] lines = sourceAt.split("\n");
		StringBuilder builder = new StringBuilder();

		for (String line : lines) {
			{
				int indexOf = line.indexOf('#');

				if (indexOf != -1) {
					line = line.substring(0, indexOf);
				}

				line = line.trim();
			}

			if (Strings.isBlank(line)) {
				builder.append(line).append('\n');
				continue;
			}

			String[] parts = line.split("\\s+");

			if (parts.length < 2) {
				logger.warn("Invalid AT Line: " + line);
				builder.append(line).append('\n');
				continue;
			}

			String originalClassName = parts[1].replace('.', '/');
			parts[1] = either(remapper.mapClass(originalClassName), parts[1]).replace('/', '.');

			if (parts.length >= 3) {
				if (parts[2].contains("(")) {
					String methodName = parts[2].substring(0, parts[2].indexOf('('));
					String methodDescriptor = parts[2].substring(parts[2].indexOf('('));
					parts[2] = either(remapper.mapMethod(originalClassName, methodName, methodDescriptor), methodName)
							+ remapDescriptor(methodDescriptor, it -> either(remapper.mapClass(it), it));
				} else {
					parts[2] = either(remapper.mapField(originalClassName, parts[2], null), parts[2]);
				}
			}

			builder.append(String.join(" ", parts)).append('\n');
		}

		return builder.toString();
	}

	private static <T> T either(@Nullable T first, @Nullable T second) {
		return first == null ? second : first;
	}
}
