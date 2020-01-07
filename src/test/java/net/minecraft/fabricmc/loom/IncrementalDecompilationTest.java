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

package net.minecraft.fabricmc.loom;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;

import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.junit.Ignore;
import org.junit.Test;

import net.fabricmc.loom.task.fernflower.ForkedFFExecutor;
import net.fabricmc.loom.task.fernflower.IncrementalDecompilation;
import net.fabricmc.loom.util.LineNumberRemapper;
import net.fabricmc.stitch.util.StitchUtil;

public class IncrementalDecompilationTest {
	private static final Path NEW_COMPILED_JAR = Paths.get("minecraft-1.15.1-build.23-not-linemapped.jar");
	private static final Path NEW_COMPILED_JAR_UNMODIFIED = Paths.get("save/minecraft-1.15.1-build.23-not-linemapped.jar");
	private static final Path OLD_COMPILED_JAR = Paths.get("minecraft-1.15.1-build.17-not-linemapped.jar");
	private static final Path OLD_SOURCES_JAR = Paths.get("minecraft-1.15.1-build.17-sources.jar");
	private static final Path OLD_LINEMAPPED_JAR = Paths.get("minecraft-1.15.1-build.17-linemapped.jar");

	private static final Path OUT_LINEMAP = Paths.get("linemap.txt");
	private static final Path OUT_SOURCES = Paths.get("minecraft-1.15.1-build.23-sources.jar");
	private static final Path OUT_LINEMAPPED_JAR = Paths.get("minecraft-1.15.1-build.23-linemapped.jar");
	private IncrementalDecompilation ic = new IncrementalDecompilation(NEW_COMPILED_JAR, OLD_COMPILED_JAR, new TestLogger());

	@Ignore
	@Test
	public void test() throws IOException {
		Files.copy(NEW_COMPILED_JAR_UNMODIFIED, NEW_COMPILED_JAR, StandardCopyOption.REPLACE_EXISTING);
		Path toDecompile = ic.getChangedClassfilesFile();

		decompile(toDecompile);

		remapLineNumbers();

		ic.addUnchangedSourceFiles(OUT_SOURCES, OLD_SOURCES_JAR);
		ic.useUnchangedLinemappedClassFiles(OLD_LINEMAPPED_JAR, OUT_LINEMAPPED_JAR);
	}

	private void remapLineNumbers() throws IOException {
		LineNumberRemapper remapper = new LineNumberRemapper();
		remapper.readMappings(OUT_LINEMAP.toFile());

		try (StitchUtil.FileSystemDelegate inFs = StitchUtil.getJarFileSystem(OLD_COMPILED_JAR.toFile(), true);
			StitchUtil.FileSystemDelegate outFs = StitchUtil.getJarFileSystem(OUT_LINEMAPPED_JAR.toFile(), true)) {
			remapper.process(null, inFs.get().getPath("/"), outFs.get().getPath("/"));
		}
	}

	private void decompile(Path toDecompile) {
		ForkedFFExecutor.runFF(
						new HashMap<String, Object>() {{
								put(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES, "1");
								put(IFernflowerPreferences.REMOVE_SYNTHETIC, "1");
								put(IFernflowerPreferences.BYTECODE_SOURCE_MAPPING, "1");
								}},
						new ArrayList<>(),
						toDecompile.toFile(),
						OUT_SOURCES.toFile(),
						OUT_LINEMAP.toFile()
		);
	}
}
