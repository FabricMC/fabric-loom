/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022 FabricMC
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

package net.fabricmc.loom.configuration.providers.mappings.tiny;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.regex.Pattern;

import com.google.common.base.Stopwatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.providers.mappings.IntermediateMappingsService;
import net.fabricmc.mappingio.adapter.MappingNsCompleter;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.tiny.Tiny2FileReader;
import net.fabricmc.mappingio.format.tiny.Tiny2FileWriter;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

public final class MappingsMerger {
	private static final Logger LOGGER = LoggerFactory.getLogger(MappingsMerger.class);

	public static void mergeAndSaveMappings(Path from, Path out, IntermediateMappingsService intermediateMappingsService) throws IOException {
		Stopwatch stopwatch = Stopwatch.createStarted();
		LOGGER.info(":merging mappings");

		MemoryMappingTree intermediaryTree = new MemoryMappingTree();
		intermediateMappingsService.getMemoryMappingTree().accept(new MappingSourceNsSwitch(intermediaryTree, MappingsNamespace.INTERMEDIARY.toString()));

		try (BufferedReader reader = Files.newBufferedReader(from, StandardCharsets.UTF_8)) {
			Tiny2FileReader.read(reader, intermediaryTree);
		}

		MemoryMappingTree officialTree = new MemoryMappingTree();
		MappingNsCompleter nsCompleter = new MappingNsCompleter(officialTree, Map.of(MappingsNamespace.OFFICIAL.toString(), MappingsNamespace.INTERMEDIARY.toString()));
		MappingSourceNsSwitch nsSwitch = new MappingSourceNsSwitch(nsCompleter, MappingsNamespace.OFFICIAL.toString());
		intermediaryTree.accept(nsSwitch);

		inheritMappedNamesOfEnclosingClasses(officialTree);

		try (var writer = new Tiny2FileWriter(Files.newBufferedWriter(out, StandardCharsets.UTF_8), false)) {
			officialTree.accept(writer);
		}

		LOGGER.info(":merged mappings in " + stopwatch.stop());
	}

	/**
	 * Searches the mapping tree for inner classes with no mapped name, whose enclosing classes have mapped names.
	 * Currently, Yarn does not export mappings for these inner classes.
	 */
	private static void inheritMappedNamesOfEnclosingClasses(MemoryMappingTree tree) {
		int intermediaryIdx = tree.getNamespaceId("intermediary");
		int namedIdx = tree.getNamespaceId("named");

		// The tree does not have an index by intermediary names by default
		tree.setIndexByDstNames(true);

		for (MappingTree.ClassMapping classEntry : tree.getClasses()) {
			String intermediaryName = classEntry.getDstName(intermediaryIdx);
			String namedName = classEntry.getDstName(namedIdx);

			if (intermediaryName.equals(namedName) && intermediaryName.contains("$")) {
				String[] path = intermediaryName.split(Pattern.quote("$"));
				int parts = path.length;

				for (int i = parts - 2; i >= 0; i--) {
					String currentPath = String.join("$", Arrays.copyOfRange(path, 0, i + 1));
					String namedParentClass = tree.mapClassName(currentPath, intermediaryIdx, namedIdx);

					if (!namedParentClass.equals(currentPath)) {
						classEntry.setDstName(namedParentClass
										+ "$" + String.join("$", Arrays.copyOfRange(path, i + 1, path.length)),
								namedIdx);
						break;
					}
				}
			}
		}
	}
}
