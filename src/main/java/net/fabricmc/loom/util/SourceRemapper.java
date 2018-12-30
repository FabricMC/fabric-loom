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

package net.fabricmc.loom.util;

import com.google.common.collect.ImmutableMap;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.providers.MappingsProvider;
import net.fabricmc.stitch.util.Pair;
import net.fabricmc.stitch.util.StitchUtil;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.io.MappingsReader;
import org.cadixdev.lorenz.io.TextMappingsReader;
import org.cadixdev.mercury.Mercury;
import org.cadixdev.mercury.remapper.MercuryRemapper;
import org.gradle.api.Project;
import org.gradle.internal.impldep.aQute.bnd.build.Run;
import org.objectweb.asm.commons.Remapper;
import org.zeroturnaround.zip.ZipUtil;

import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SourceRemapper {
	public static void remapSources(Project project, File source, File destination, boolean toNamed) throws Exception {
		remapSourcesInner(project, source, destination, toNamed);
		// TODO: FIXME - WORKAROUND https://github.com/FabricMC/fabric-loom/issues/45
		System.gc();
	}

	private static void remapSourcesInner(Project project, File source, File destination, boolean toNamed) throws Exception {

		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		MappingsProvider mappingsProvider = extension.getMappingsProvider();

		MappingSet mappings = extension.getOrCreateSrcMappingCache(toNamed ? 1 : 0, () -> {
			try {
				project.getLogger().lifecycle(":loading " + (toNamed ? "intermediary -> named" : "named -> intermediary") + " source mappings");

				FileReader mappingsReader = new FileReader(mappingsProvider.MAPPINGS_TINY);
				MappingSet mappingsOut = new TinyReader(mappingsReader, toNamed ? "intermediary" : "named", toNamed ? "named" : "intermediary").read();
				mappingsReader.close();
				return mappingsOut;
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});

		project.getLogger().lifecycle(":remapping source jar");

		Mercury mercury = new Mercury();

		for (File file : project.getConfigurations().getByName(Constants.MINECRAFT_DEPENDENCIES).getFiles()) {
			mercury.getClassPath().add(file.toPath());
		}
		if (!toNamed) {
			for (File file : project.getConfigurations().getByName(Constants.COMPILE_MODS_MAPPED).getFiles()) {
				mercury.getClassPath().add(file.toPath());
			}
		}
		for (File file : extension.getUnmappedMods()) {
			if (file.isFile()) {
				mercury.getClassPath().add(file.toPath());
			}
		}

		mercury.getClassPath().add(extension.getMinecraftMappedProvider().MINECRAFT_MAPPED_JAR.toPath());
		mercury.getClassPath().add(extension.getMinecraftMappedProvider().MINECRAFT_INTERMEDIARY_JAR.toPath());

		mercury.getProcessors().add(MercuryRemapper.create(mappings));

		if (source.equals(destination)) {
			if (source.isDirectory()) {
				throw new RuntimeException("Directories must differ!");
			}

			source = new File(destination.getAbsolutePath().substring(0, destination.getAbsolutePath().lastIndexOf('.')) + "-dev.jar");
			try {
				com.google.common.io.Files.move(destination, source);
			} catch (IOException e) {
				throw new RuntimeException("Could not rename " + destination.getName() + "!", e);
			}
		}

		Path srcPath = source.toPath();
		boolean isSrcTmp = false;
		if (!source.isDirectory()) {
			// create tmp directory
			isSrcTmp = true;
			srcPath = Files.createTempDirectory("fabric-loom-src");
			ZipUtil.unpack(source, srcPath.toFile());
		}

		if (!destination.isDirectory() && destination.exists()) {
			if (!destination.delete()) {
				throw new RuntimeException("Could not delete " + destination.getName() + "!");
			}
		}

		StitchUtil.FileSystemDelegate dstFs = destination.isDirectory() ? null : StitchUtil.getJarFileSystem(destination, true);
		Path dstPath = dstFs != null ? dstFs.get().getPath("/") : destination.toPath();

		try {
			mercury.rewrite(srcPath, dstPath);
		} catch (Exception e) {
			project.getLogger().warn("Could not remap " + source.getName() + " fully!", e);
		}

		if (dstFs != null) {
			dstFs.close();
		}

		if (isSrcTmp) {
			Files.walkFileTree(srcPath, new DeletingFileVisitor());
		}
	}

	static class SimpleClassMapper extends Remapper {
		final Map<String, String> classMap;

		public SimpleClassMapper(Map<String, String> map) {
			this.classMap = map;
		}

		public String map(String typeName) {
			return this.classMap.getOrDefault(typeName, typeName);
		}
	}

	// With help from jamierocks; heavily modified
	public static class TinyReader extends MappingsReader {
		private final Reader reader;
		private final String to;
		private final String from;

		private int toOffset = 0;
		private int fromOffset = 1;
		private int lineNumber = 0;

		public TinyReader(Reader reader, String from, String to) {
			this.reader = reader;
			this.from = from;
			this.to = to;
		}

		//This looks at the first line of the tiny file and finds the column of the mappings, horrible but works.
		public Pair<Integer, Integer> getMappingOffset(String to, String from, String line){
			int toOffset = -1;
			int fromOffset = -1;
			String[] split = line.split("\t");
			for (int i = 0; i < split.length; i++) {
				String mapping = split[i];
				if(mapping.equalsIgnoreCase(to)){
					fromOffset = i -1;
				}
				if(mapping.equalsIgnoreCase(from)){
					toOffset = i -1;
				}
			}
			return Pair.of(toOffset, fromOffset);
		}

		@Override
		public MappingSet read(final MappingSet mappings) throws IOException {
			// As TinyV1 stores descriptors in obfuscated format always, we need to take a two-step approach.
			Map<String, String> classNames = new HashMap<>();
			List<String[]> otherNames = new ArrayList<>();
			SimpleClassMapper classMapper = new SimpleClassMapper(classNames);
			BufferedReader br = new BufferedReader(reader);

			br.lines().forEach((line) -> {
				final String[] params = line.split("\t");
				if ((lineNumber++) == 0) {
					if (params.length < 1) {
						throw new RuntimeException("Invalid mapping file!");
					} else if (params.length < 3 || !params[0].equals("v1")) {
						throw new RuntimeException("Invalid mapping version: '" + params[0] + "'!");
					}

					Pair<Integer, Integer> offset = getMappingOffset(to, from, line);
					// System.out.println("To: " + to + " From: " + from + " toOffset:" + offset.getLeft() + " from:" + offset.getRight());
					toOffset = offset.getLeft();
					fromOffset = offset.getRight();
				} else {
					switch (params[0]) {
						case "CLASS": {
							mappings.getOrCreateClassMapping(params[1 + toOffset])
									.setDeobfuscatedName(params[1 + fromOffset]);
							classNames.put(params[1], params[1 + toOffset]);
							// System.out.println("Class " + params[1 + toOffset] + " -> " + params[1 + fromOffset]);
							return;
						}
						case "FIELD":
						case "METHOD": {
							otherNames.add(params);
							return;
						}
					}
				}
			});
			br.close();

			for (String[] params : otherNames) {
				switch (params[0]) {
					case "FIELD": {
						mappings.getOrCreateClassMapping(classMapper.map(params[1]))
								.getOrCreateFieldMapping(params[3 + toOffset], classMapper.mapDesc(params[2]))
								.setDeobfuscatedName(params[3 + fromOffset]);

						// System.out.println("Field " + params[3 + toOffset] + classMapper.mapDesc(params[2]) + " -> " + params[3 + fromOffset]);
						break;
					}
					case "METHOD": {
						mappings.getOrCreateClassMapping(classMapper.map(params[1]))
								.getOrCreateMethodMapping(params[3 + toOffset], classMapper.mapMethodDesc(params[2]))
								.setDeobfuscatedName(params[3 + fromOffset]);

						// System.out.println("Method " + params[3 + toOffset] + classMapper.mapMethodDesc(params[2]) + " -> " + params[3 + fromOffset]);
						break;
					}
				}
			}

			return mappings;
		}

		@Override
		public void close() throws IOException {

		}
	}

}
