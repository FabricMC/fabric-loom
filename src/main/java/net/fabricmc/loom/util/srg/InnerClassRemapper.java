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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import org.zeroturnaround.zip.ZipUtil;

import net.fabricmc.mapping.tree.ClassDef;
import net.fabricmc.mapping.tree.TinyTree;
import net.fabricmc.tinyremapper.IMappingProvider;

public class InnerClassRemapper {
	public static IMappingProvider of(Path fromJar, TinyTree mappingsWithSrg, String from, String to) throws IOException {
		return sink -> {
			remapInnerClass(fromJar, mappingsWithSrg, from, to, sink::acceptClass);
		};
	}

	private static void remapInnerClass(Path fromJar, TinyTree mappingsWithSrg, String from, String to, BiConsumer<String, String> action) {
		try (InputStream inputStream = Files.newInputStream(fromJar)) {
			Map<String, String> availableClasses = mappingsWithSrg.getClasses().stream()
					.collect(Collectors.groupingBy(classDef -> classDef.getName(from),
							Collectors.<ClassDef, String>reducing(
									null,
									classDef -> classDef.getName(to),
									(first, last) -> last
							))
					);
			ZipUtil.iterate(inputStream, (in, zipEntry) -> {
				if (!zipEntry.isDirectory() && zipEntry.getName().contains("$") && zipEntry.getName().endsWith(".class")) {
					String className = zipEntry.getName().substring(0, zipEntry.getName().length() - 6);

					if (!availableClasses.containsKey(className)) {
						String parentName = className.substring(0, className.indexOf('$'));
						String childName = className.substring(className.indexOf('$') + 1);
						String remappedParentName = availableClasses.getOrDefault(parentName, parentName);
						String remappedName = remappedParentName + "$" + childName;

						if (!className.equals(remappedName)) {
							action.accept(className, remappedName);
						}
					}
				}
			});
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
