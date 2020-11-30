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

package net.fabricmc.loom.inject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import cpw.mods.modlauncher.api.INameMappingService;

import net.fabricmc.mapping.tree.TinyMappingFactory;
import net.fabricmc.mapping.tree.TinyTree;

public class YarnNamingService implements INameMappingService {
	private static final String PATH_TO_MAPPINGS = "fabric.yarnWithSrg.path";
	private TinyTree mappings = null;

	@Override
	public String mappingName() {
		return "srgtoyarn";
	}

	@Override
	public String mappingVersion() {
		return "1";
	}

	@Override
	public Map.Entry<String, String> understanding() {
		return new Pair<>("srg", "mcp");
	}

	@Override
	public BiFunction<Domain, String, String> namingFunction() {
		return this::remap;
	}

	private TinyTree getMappings() {
		if (mappings != null) {
			return mappings;
		}

		String pathStr = System.getProperty(PATH_TO_MAPPINGS);
		if (pathStr == null) throw new RuntimeException("Missing system property '" + PATH_TO_MAPPINGS + "'!");
		Path path = Paths.get(pathStr);

		try (BufferedReader reader = Files.newBufferedReader(path)) {
			mappings = TinyMappingFactory.loadWithDetection(reader);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		return mappings;
	}

	private String remap(Domain domain, String name) {
		TinyTree mappings = getMappings();

		switch (domain) {
		case CLASS:
			boolean dot = name.contains(".");
			return find(mappings.getClasses(), def -> maybeReplace(dot, def.getName("srg"), '/', '.').equals(name))
					.map(def -> maybeReplace(dot, def.getName("named"), '/', '.'))
					.orElse(name);
		case METHOD:
			return mappings.getClasses().stream()
					.flatMap(def -> def.getMethods().stream())
					.filter(def -> def.getName("srg").equals(name))
					.findAny()
					.map(def -> def.getName("named"))
					.orElse(name);
		case FIELD:
			return mappings.getClasses().stream()
					.flatMap(def -> def.getFields().stream())
					.filter(def -> def.getName("srg").equals(name))
					.findAny()
					.map(def -> def.getName("named"))
					.orElse(name);
		default:
			return name;
		}
	}

	// From CollectionUtil
	private static <E> Optional<E> find(Iterable<? extends E> collection, Predicate<? super E> filter) {
		for (E e : collection) {
			if (filter.test(e)) {
				return Optional.of(e);
			}
		}

		return Optional.empty();
	}

	private static String maybeReplace(boolean run, String s, char from, char to) {
		return run ? s.replace(from, to) : s;
	}
}
