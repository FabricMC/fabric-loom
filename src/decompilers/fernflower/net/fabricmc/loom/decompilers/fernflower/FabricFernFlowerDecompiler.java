/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2019-2021 FabricMC
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

package net.fabricmc.loom.decompilers.fernflower;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.jetbrains.java.decompiler.main.Fernflower;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;
import org.jetbrains.java.decompiler.util.InterpreterUtil;

import net.fabricmc.fernflower.api.IFabricJavadocProvider;
import net.fabricmc.loom.decompilers.LoomInternalDecompiler;

public final class FabricFernFlowerDecompiler implements LoomInternalDecompiler {
	@Override
	public void decompile(LoomInternalDecompiler.Context context) {
		Path sourcesDestination = context.sourcesDestination();
		Path linemapDestination = context.linemapDestination();

		final Map<String, Object> options = new HashMap<>(
				Map.of(
					IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES, "1",
					IFernflowerPreferences.BYTECODE_SOURCE_MAPPING, "1",
					IFernflowerPreferences.REMOVE_SYNTHETIC, "1",
					IFernflowerPreferences.LOG_LEVEL, "trace",
					IFernflowerPreferences.THREADS, String.valueOf(context.numberOfThreads()),
					IFernflowerPreferences.INDENT_STRING, "\t",
					IFabricJavadocProvider.PROPERTY_NAME, new TinyJavadocProvider(context.javaDocs().toFile())
				)
		);

		options.putAll(context.options());

		IResultSaver saver = new ThreadSafeResultSaver(sourcesDestination::toFile, linemapDestination::toFile);
		Fernflower ff = new Fernflower((externalPath, internalPath) -> FabricFernFlowerDecompiler.this.getBytecode(externalPath, internalPath, context), saver, options, new FernflowerLogger(context.logger()));

		for (Path library : context.libraries()) {
			ff.addLibrary(library.toFile());
		}

		ff.addSource(context.compiledJar().toFile());

		try {
			ff.decompileContext();
		} finally {
			ff.clearContext();
		}
	}

	private byte[] getBytecode(String externalPath, String internalPath, LoomInternalDecompiler.Context context) throws IOException {
		File file = new File(externalPath);

		if (internalPath == null) {
			return InterpreterUtil.getBytes(file);
		} else {
			return context.unpackZip(file.toPath(), internalPath);
		}
	}
}
