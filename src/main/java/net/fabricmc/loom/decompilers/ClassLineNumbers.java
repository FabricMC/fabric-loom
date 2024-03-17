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

package net.fabricmc.loom.decompilers;

import static java.text.MessageFormat.format;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

public record ClassLineNumbers(Map<String, ClassLineNumbers.Entry> lineMap) {
	public ClassLineNumbers {
		Objects.requireNonNull(lineMap, "lineMap");

		if (lineMap.isEmpty()) {
			throw new IllegalArgumentException("lineMap is empty");
		}
	}

	public static ClassLineNumbers readMappings(Path lineMappingsPath) {
		try (BufferedReader reader = Files.newBufferedReader(lineMappingsPath)) {
			return readMappings(reader);
		} catch (IOException e) {
			throw new UncheckedIOException("Exception reading LineMappings file.", e);
		}
	}

	public static ClassLineNumbers readMappings(BufferedReader reader) {
		var lineMap = new HashMap<String, ClassLineNumbers.Entry>();

		String line = null;
		int lineNumber = 0;

		record CurrentClass(String className, int maxLine, int maxLineDest) {
			void putEntry(Map<String, ClassLineNumbers.Entry> entries, Map<Integer, Integer> mappings) {
				var entry = new ClassLineNumbers.Entry(className(), maxLine(), maxLineDest(), Collections.unmodifiableMap(mappings));

				final ClassLineNumbers.Entry previous = entries.put(className(), entry);

				if (previous != null) {
					throw new IllegalStateException("Duplicate class line mappings for " + className());
				}
			}
		}

		CurrentClass currentClass = null;
		Map<Integer, Integer> currentMappings = new HashMap<>();

		try {
			while ((line = reader.readLine()) != null) {
				if (line.isEmpty()) {
					continue;
				}

				final String[] segments = line.trim().split("\t");

				if (line.charAt(0) != '\t') {
					if (currentClass != null) {
						currentClass.putEntry(lineMap, currentMappings);
						currentMappings = new HashMap<>();
					}

					currentClass = new CurrentClass(segments[0], Integer.parseInt(segments[1]), Integer.parseInt(segments[2]));
				} else {
					Objects.requireNonNull(currentClass, "No class line mappings found for line " + lineNumber);
					currentMappings.put(Integer.parseInt(segments[0]), Integer.parseInt(segments[1]));
				}

				lineNumber++;
			}
		} catch (Exception e) {
			throw new RuntimeException(format("Exception reading mapping line @{0}: {1}", lineNumber, line), e);
		}

		assert currentClass != null;
		currentClass.putEntry(lineMap, currentMappings);

		return new ClassLineNumbers(Collections.unmodifiableMap(lineMap));
	}

	public void write(Writer writer) throws IOException {
		for (Map.Entry<String, ClassLineNumbers.Entry> entry : lineMap.entrySet()) {
			entry.getValue().write(writer);
		}
	}

	/**
	 * Merge two ClassLineNumbers together, throwing an exception if there are any duplicate class line mappings.
	 */
	@Nullable
	public static ClassLineNumbers merge(@Nullable ClassLineNumbers a, @Nullable ClassLineNumbers b) {
		if (a == null) {
			return b;
		} else if (b == null) {
			return a;
		}

		var lineMap = new HashMap<>(a.lineMap());

		for (Map.Entry<String, Entry> entry : b.lineMap().entrySet()) {
			lineMap.merge(entry.getKey(), entry.getValue(), (v1, v2) -> {
				throw new IllegalStateException("Duplicate class line mappings for " + entry.getKey());
			});
		}

		return new ClassLineNumbers(Collections.unmodifiableMap(lineMap));
	}

	public record Entry(String className, int maxLine, int maxLineDest, Map<Integer, Integer> lineMap) {
		public void write(Writer writer) throws IOException {
			writer.write(className);
			writer.write('\t');
			writer.write(Integer.toString(maxLine));
			writer.write('\t');
			writer.write(Integer.toString(maxLineDest));
			writer.write('\n');

			for (Map.Entry<Integer, Integer> lineEntry : lineMap.entrySet()) {
				writer.write('\t');
				writer.write(Integer.toString(lineEntry.getKey()));
				writer.write('\t');
				writer.write(Integer.toString(lineEntry.getValue()));
				writer.write('\n');
			}
		}
	}
}
