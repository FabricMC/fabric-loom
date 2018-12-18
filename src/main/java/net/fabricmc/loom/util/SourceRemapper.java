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

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.providers.MappingsProvider;
import net.fabricmc.stitch.util.Pair;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.io.TextMappingsReader;
import org.cadixdev.mercury.Mercury;
import org.cadixdev.mercury.remapper.MercuryRemapper;
import org.gradle.api.Project;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;

public class SourceRemapper {

	public static void remapSources(Project project) throws Exception {

		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		MappingsProvider mappingsProvider = extension.getMappingsProvider();

		project.getLogger().lifecycle("Setting up source remapper");

		FileReader mappingsReader = new FileReader(mappingsProvider.MAPPINGS_TINY);
		MappingSet mappings = new TinyReader(mappingsReader, "intermediary", "named").read();
		mappingsReader.close();

		Mercury mercury = new Mercury();

		for (File file : project.getConfigurations().getByName("compile").getFiles()) {
			mercury.getClassPath().add(file.toPath());
		}
		for (File file : extension.getUnmappedMods()) {
			if (file.isFile()) {
				mercury.getClassPath().add(file.toPath());
			}
		}

		mercury.getClassPath().add(extension.getMinecraftMappedProvider().MINECRAFT_INTERMEDIARY_JAR.toPath());
		mercury.getClassPath().add(extension.getMinecraftMappedProvider().MINECRAFT_MAPPED_JAR.toPath());

		mercury.getProcessors().add(MercuryRemapper.create(mappings));

		project.getLogger().lifecycle("Remapping source");
		mercury.rewrite(new File(project.getRootDir(), "src/main/java").toPath(), new File(project.getRootDir(), "src_mapped").toPath());
	}

	//Thanks jamierocks
	public static class TinyReader extends TextMappingsReader {

		public TinyReader(final Reader reader, String to, String from) {
			super(reader, mappingSet -> new Processor(mappingSet, to, from));
		}

		public static class Processor extends TextMappingsReader.Processor {

			String to;
			String from;

			int toOffset = 0;
			int fromOffset = 1;

			public Processor(MappingSet mappings, String to, String from) {
				super(mappings);
				this.to = to;
				this.from = from;
			}

			@Override
			public void accept(final String line) {
				final String[] params = line.split("\t");
				switch (params[0]) {
					case "v1":
						Pair<Integer, Integer> offset = getMappingOffset(to, from, line);
						System.out.println("To: " + to + " From: " + from + " toOffset:" + offset.getLeft() + " from:" + offset.getRight());
						toOffset = offset.getLeft();
						fromOffset = offset.getRight();
						return;
					case "CLASS": {
						this.mappings.getOrCreateClassMapping(params[1 + toOffset])
							.setDeobfuscatedName(params[1 + fromOffset]);
						System.out.println("Class " + params[1 + toOffset] + " -> " + params[1 + fromOffset]);
						return;
					}
					case "FIELD": {
						this.mappings.getOrCreateClassMapping(params[1 + toOffset])
							// params[2] is the descriptor
							.getOrCreateFieldMapping(params[3 + toOffset])
							.setDeobfuscatedName(params[3 + fromOffset]);

						System.out.println("Field " + params[3 + toOffset] + " -> " + params[3 + fromOffset]);
						return;
					}
					case "METHOD": {
						this.mappings.getOrCreateClassMapping(params[1 + toOffset])
							.getOrCreateMethodMapping(params[3 + toOffset], params[2])
							.setDeobfuscatedName(params[3 + fromOffset]);

						System.out.println("Method " + params[3 + toOffset] + " -> " + params[3 + fromOffset]);
						return;
					}
				}
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

		}

	}


}
