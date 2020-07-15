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

package net.fabricmc.loom.decompilers.cfr;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import org.benf.cfr.reader.api.CfrDriver;
import org.benf.cfr.reader.api.ClassFileSource;
import org.benf.cfr.reader.api.OutputSinkFactory;
import org.benf.cfr.reader.api.SinkReturns;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.gradle.api.Project;

import net.fabricmc.loom.api.decompilers.DecompilationMetadata;
import net.fabricmc.loom.api.decompilers.LoomDecompiler;
import net.fabricmc.loom.util.progress.ProgressLogger;

public class FabricCFRDecompiler implements LoomDecompiler {
	private final Project project;

	public FabricCFRDecompiler(Project project) {
		this.project = project;
	}

	@Override
	public String name() {
		return "cfr";
	}

	@Override
	public void decompile(Path compiledJar, Path sourcesDestination, Path linemapDestination, DecompilationMetadata metaData) {
		project.getLogger().lifecycle(sourcesDestination.toString());

		ProgressLogger progressLogger = ProgressLogger.getProgressFactory(project, getClass().getName());
		progressLogger.start("Decompiling using CFR", "cfr");

		Manifest manifest = new Manifest();
		manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
		Set<String> addedDirectories = new HashSet<>();

		try (OutputStream fos = Files.newOutputStream(sourcesDestination); JarOutputStream jos = new JarOutputStream(fos, manifest); ZipFile inputZip = new ZipFile(compiledJar.toFile())) {
			CfrDriver driver = new CfrDriver.Builder()
					.withOptions(ImmutableMap.of(
							"renameillegalidents", "true",
							"trackbytecodeloc", "true"
					))
					.withClassFileSource(new ClassFileSource() {
						@Override
						public void informAnalysisRelativePathDetail(String usePath, String classFilePath) {
						}

						@Override
						public Collection<String> addJar(String jarPath) {
							return null;
						}

						@Override
						public String getPossiblyRenamedPath(String path) {
							return path;
						}

						@Override
						public synchronized Pair<byte[], String> getClassFileContent(String path) throws IOException {
							ZipEntry zipEntry = inputZip.getEntry(path);

							if (zipEntry == null) {
								throw new FileNotFoundException(path);
							}

							try (InputStream inputStream = inputZip.getInputStream(zipEntry)) {
								return Pair.make(ByteStreams.toByteArray(inputStream), path);
							}
						}
					})
					.withOutputSink(new OutputSinkFactory() {
						@Override
						public List<SinkClass> getSupportedSinks(SinkType sinkType, Collection<SinkClass> available) {
							switch (sinkType) {
								case PROGRESS:
									return Collections.singletonList(SinkClass.STRING);
								case JAVA:
									return Collections.singletonList(SinkClass.DECOMPILED);
								case LINENUMBER:
									return Collections.singletonList(SinkClass.LINE_NUMBER_MAPPING);
								default:
									return Collections.emptyList();
							}
						}

						@SuppressWarnings("unchecked")
						@Override
						public <T> Sink<T> getSink(SinkType sinkType, SinkClass sinkClass) {
							switch (sinkType) {
								case PROGRESS:
									return (p) -> progressLogger.progress((String) p);
								case JAVA:
									return (Sink<T>) decompiledSink(jos, addedDirectories);
								case LINENUMBER:
									return (Sink<T>) lineNumberMappingsSink();
								case EXCEPTION:
									return (e) -> project.getLogger().error((String) e);
							}

							return null;
						}
					})
					.build();

			List<String> classes = Collections.list(inputZip.entries()).stream()
									.map(ZipEntry::getName)
									.filter(input -> input.endsWith(".class"))
									.collect(Collectors.toList());

			driver.analyse(classes);
		} catch (IOException e) {
			throw new RuntimeException("Failed to decompile", e);
		}
	}

	private static OutputSinkFactory.Sink<SinkReturns.Decompiled> decompiledSink(JarOutputStream jos, Set<String> addedDirectories) {
		return decompiled -> {
			String filename = decompiled.getPackageName().replace('.', '/');
			if (!filename.isEmpty()) filename += "/";
			filename += decompiled.getClassName() + ".java";

			String[] path = filename.split("/");
			String pathPart = "";

			for (int i = 0; i < path.length - 1; i++) {
				pathPart += path[i] + "/";

				if (addedDirectories.add(pathPart)) {
					JarEntry entry = new JarEntry(pathPart);
					entry.setTime(new Date().getTime());

					try {
						jos.putNextEntry(entry);
						jos.closeEntry();
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				}
			}

			byte[] data = decompiled.getJava().getBytes(Charsets.UTF_8);
			JarEntry entry = new JarEntry(filename);
			entry.setTime(new Date().getTime());
			entry.setSize(data.length);

			try {
				jos.putNextEntry(entry);
				jos.write(data);
				jos.closeEntry();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		};
	}

	private static OutputSinkFactory.Sink<SinkReturns.LineNumberMapping_DO_NOT_USE> lineNumberMappingsSink() {
		return lineNumberMappings -> {
			System.out.println(lineNumberMappings);
			System.out.println(lineNumberMappings.getMapping());
		};
	}
}

