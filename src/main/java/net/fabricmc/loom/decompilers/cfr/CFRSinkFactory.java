/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021 FabricMC
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

package net.fabricmc.loom.decompilers.cfr;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import com.google.common.base.Charsets;
import org.benf.cfr.reader.api.OutputSinkFactory;
import org.benf.cfr.reader.api.SinkReturns;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CFRSinkFactory implements OutputSinkFactory {
	private static final Logger LOGGER = LoggerFactory.getLogger(CFRSinkFactory.class);

	private final JarOutputStream outputStream;
	private final Set<String> addedDirectories = new HashSet<>();
	private final Map<String, Map<Integer, Integer>> lineMap = new HashMap<>();

	public CFRSinkFactory(JarOutputStream outputStream) {
		this.outputStream = outputStream;
	}

	@Override
	public List<SinkClass> getSupportedSinks(SinkType sinkType, Collection<SinkClass> available) {
		return switch (sinkType) {
		case PROGRESS -> Collections.singletonList(SinkClass.STRING);
		case JAVA -> Collections.singletonList(SinkClass.DECOMPILED);
		case LINENUMBER -> Collections.singletonList(SinkClass.LINE_NUMBER_MAPPING);
		default -> Collections.emptyList();
		};
	}

	@Override
	public <T> Sink<T> getSink(SinkType sinkType, SinkClass sinkClass) {
		return switch (sinkType) {
		case PROGRESS -> (p) -> LOGGER.debug((String) p);
		case JAVA -> (Sink<T>) decompiledSink();
		case LINENUMBER -> (Sink<T>) lineNumberMappingSink();
		case EXCEPTION -> (e) -> LOGGER.error((String) e);
		default -> null;
		};
	}

	private Sink<SinkReturns.Decompiled> decompiledSink() {
		return sinkable -> {
			String filename = sinkable.getPackageName().replace('.', '/');
			if (!filename.isEmpty()) filename += "/";
			filename += sinkable.getClassName() + ".java";

			byte[] data = sinkable.getJava().getBytes(Charsets.UTF_8);

			LOGGER.info(filename);
			writeToJar(filename, data);
		};
	}

	private Sink<SinkReturns.LineNumberMapping> lineNumberMappingSink() {
		return sinkable -> {
			final String className = sinkable.getClassName();
			final NavigableMap<Integer, Integer> classFileMappings = sinkable.getClassFileMappings();
			final NavigableMap<Integer, Integer> mappings = sinkable.getMappings();

			if (classFileMappings == null || mappings == null) return;

			for (Map.Entry<Integer, Integer> entry : mappings.entrySet()) {
				// Line mapping in the original jar
				Integer srcLineNumber = entry.getValue();
				// New line number
				Integer dstLineNumber = classFileMappings.get(entry.getKey());

				if (srcLineNumber == null || dstLineNumber == null) continue;

				lineMap.computeIfAbsent(className, (c) -> new HashMap<>()).put(srcLineNumber, dstLineNumber);
			}
		};
	}

	private synchronized void writeToJar(String filename, byte[] data) {
		String[] path = filename.split("/");
		String pathPart = "";

		for (int i = 0; i < path.length - 1; i++) {
			pathPart += path[i] + "/";

			if (addedDirectories.add(pathPart)) {
				JarEntry entry = new JarEntry(pathPart);
				entry.setTime(new Date().getTime());

				try {
					outputStream.putNextEntry(entry);
					outputStream.closeEntry();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		}

		JarEntry entry = new JarEntry(filename);
		entry.setTime(new Date().getTime());
		entry.setSize(data.length);

		try {
			outputStream.putNextEntry(entry);
			outputStream.write(data);
			outputStream.closeEntry();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public Map<String, Map<Integer, Integer>> getLineMap() {
		return Collections.unmodifiableMap(lineMap);
	}
}
