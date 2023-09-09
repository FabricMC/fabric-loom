/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2023 FabricMC
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

package net.fabricmc.loom.decompilers;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;

// This is an internal interface to loom, DO NOT USE this in your own plugins.
public interface LoomInternalDecompiler {
	void decompile(Context context);

	interface Context {
		Path compiledJar();

		Path sourcesDestination();

		Path linemapDestination();

		int numberOfThreads();

		Path javaDocs();

		Collection<Path> libraries();

		Logger logger();

		Map<String, String> options();

		byte[] unpackZip(Path zip, String path) throws IOException;
	}

	interface Logger {
		void accept(String data) throws IOException;

		void error(String msg);
	}
}
