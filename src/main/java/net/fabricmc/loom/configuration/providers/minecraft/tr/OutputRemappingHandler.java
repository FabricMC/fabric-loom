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

package net.fabricmc.loom.configuration.providers.minecraft.tr;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.function.BiConsumer;

import dev.architectury.tinyremapper.InputTag;
import dev.architectury.tinyremapper.TinyRemapper;

import net.fabricmc.loom.util.FileSystemUtil;
import net.fabricmc.loom.util.FileSystemUtil.FileSystemDelegate;
import net.fabricmc.loom.util.ThreadingUtils;

public class OutputRemappingHandler {
	public static void remap(TinyRemapper remapper, Path assets, Path output) throws IOException {
		remap(remapper, assets, output, null);
	}

	public static void remap(TinyRemapper remapper, Path assets, Path output, BiConsumer<String, byte[]> then) throws IOException {
		remap(remapper, assets, output, then, (InputTag[]) null);
	}

	public static void remap(TinyRemapper remapper, Path assets, Path output, BiConsumer<String, byte[]> then, InputTag... inputTags) throws IOException {
		Files.copy(assets, output, StandardCopyOption.REPLACE_EXISTING);

		try (FileSystemDelegate system = FileSystemUtil.getJarFileSystem(output, true)) {
			ThreadingUtils.TaskCompleter taskCompleter = ThreadingUtils.taskCompleter();

			remapper.apply((path, bytes) -> {
				if (path.startsWith("/")) path = path.substring(1);

				try {
					Path fsPath = system.get().getPath(path + ".class");

					if (fsPath.getParent() != null) {
						Files.createDirectories(fsPath.getParent());
					}

					taskCompleter.add(() -> {
						Files.write(fsPath, bytes, StandardOpenOption.CREATE);
					});

					if (then != null) {
						then.accept(path, bytes);
					}
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			}, inputTags);

			taskCompleter.complete();
		}
	}
}
