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
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.benf.cfr.reader.Driver;
import org.benf.cfr.reader.state.ClassFileSourceImpl;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.util.AnalysisType;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;
import org.benf.cfr.reader.util.output.SinkDumperFactory;

import net.fabricmc.loom.api.decompilers.DecompilationMetadata;
import net.fabricmc.loom.api.decompilers.LoomDecompiler;

public final class LoomCFRDecompiler implements LoomDecompiler {
	private static final Map<String, String> DECOMPILE_OPTIONS = Map.of(
			"renameillegalidents", "true",
			"trackbytecodeloc", "true",
			"comments", "false"
	);

	@Override
	public void decompile(Path compiledJar, Path sourcesDestination, Path linemapDestination, DecompilationMetadata metaData) {
		final String path = compiledJar.toAbsolutePath().toString();
		final Map<String, String> allOptions = new HashMap<>(DECOMPILE_OPTIONS);
		allOptions.putAll(metaData.options());

		final Options options = OptionsImpl.getFactory().create(allOptions);

		ClassFileSourceImpl classFileSource = new ClassFileSourceImpl(options);

		for (Path library : metaData.libraries()) {
			classFileSource.addJarContent(library.toAbsolutePath().toString(), AnalysisType.JAR);
		}

		classFileSource.informAnalysisRelativePathDetail(null, null);

		DCCommonState state = new DCCommonState(options, classFileSource);

		if (metaData.javaDocs() != null) {
			state = new DCCommonState(state, new CFRObfuscationMapping(metaData.javaDocs()));
		}

		final Manifest manifest = new Manifest();
		manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");

		Map<String, Map<Integer, Integer>> lineMap;

		try (JarOutputStream outputStream = new JarOutputStream(Files.newOutputStream(sourcesDestination), manifest)) {
			CFRSinkFactory cfrSinkFactory = new CFRSinkFactory(outputStream, metaData.logger());
			SinkDumperFactory dumperFactory = new SinkDumperFactory(cfrSinkFactory, options);

			Driver.doJar(state, path, AnalysisType.JAR, dumperFactory);

			lineMap = cfrSinkFactory.getLineMap();
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to decompile", e);
		}

		writeLineMap(linemapDestination, lineMap);
	}

	private void writeLineMap(Path output, Map<String, Map<Integer, Integer>> lineMap) {
		try (Writer writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
			for (Map.Entry<String, Map<Integer, Integer>> classEntry : lineMap.entrySet()) {
				final String name = classEntry.getKey().replace(".", "/");

				final Map<Integer, Integer> mapping = classEntry.getValue();

				int maxLine = 0;
				int maxLineDest = 0;
				StringBuilder builder = new StringBuilder();

				for (Map.Entry<Integer, Integer> mappingEntry : mapping.entrySet()) {
					final int src = mappingEntry.getKey();
					final int dst = mappingEntry.getValue();

					maxLine = Math.max(maxLine, src);
					maxLineDest = Math.max(maxLineDest, dst);

					builder.append("\t").append(src).append("\t").append(dst).append("\n");
				}

				writer.write("%s\t%d\t%d\n".formatted(name, maxLine, maxLineDest));
				writer.write(builder.toString());
				writer.write("\n");
			}
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to write line map", e);
		}
	}
}
