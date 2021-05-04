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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import dev.architectury.tinyremapper.IMappingProvider;

import net.fabricmc.loom.util.FileSystemUtil;
import net.fabricmc.loom.util.FileSystemUtil.FileSystemDelegate;
import net.fabricmc.mapping.tree.ClassDef;
import net.fabricmc.mapping.tree.TinyTree;

public class InnerClassRemapper {
	public static IMappingProvider of(Path fromJar, TinyTree mappingsWithSrg, String from, String to) throws IOException {
		return sink -> {
			remapInnerClass(fromJar, mappingsWithSrg, from, to, sink::acceptClass);
		};
	}

	private static void remapInnerClass(Path fromJar, TinyTree mappingsWithSrg, String from, String to, BiConsumer<String, String> action) {
		try (FileSystemDelegate system = FileSystemUtil.getJarFileSystem(fromJar, false)) {
			Map<String, String> availableClasses = mappingsWithSrg.getClasses().stream()
					.collect(Collectors.groupingBy(classDef -> classDef.getName(from),
							Collectors.<ClassDef, String>reducing(
									null,
									classDef -> classDef.getName(to),
									(first, last) -> last
							))
					);
			Iterator<Path> iterator = Files.walk(system.get().getPath("/")).iterator();

			while (iterator.hasNext()) {
				Path path = iterator.next();
				String name = path.toString();
				if (name.startsWith("/")) name = name.substring(1);

				if (!Files.isDirectory(path) && name.contains("$") && name.endsWith(".class")) {
					String className = name.substring(0, name.length() - 6);

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
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
