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

package net.fabricmc.loom.configuration.accesswidener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.fabricmc.classtweaker.api.ClassTweakerReader;
import net.fabricmc.classtweaker.api.visitor.ClassTweakerVisitor;
import net.fabricmc.classtweaker.visitors.ClassTweakerRemapperVisitor;
import net.fabricmc.classtweaker.visitors.TransitiveOnlyFilter;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.util.LazyCloseable;
import net.fabricmc.loom.util.fmj.FabricModJson;
import net.fabricmc.loom.util.fmj.ModEnvironment;
import net.fabricmc.tinyremapper.TinyRemapper;

/**
 * {@link AccessWidenerEntry} implementation for a {@link FabricModJson}.
 */
public record ModAccessWidenerEntry(FabricModJson mod, String path, ModEnvironment environment, boolean transitiveOnly) implements AccessWidenerEntry {
	public static List<ModAccessWidenerEntry> readAll(FabricModJson modJson, boolean transitiveOnly) {
		var entries = new ArrayList<ModAccessWidenerEntry>();

		for (Map.Entry<String, ModEnvironment> entry : modJson.getClassTweakers().entrySet()) {
			entries.add(new ModAccessWidenerEntry(modJson, entry.getKey(), entry.getValue(), transitiveOnly));
		}

		return Collections.unmodifiableList(entries);
	}

	@Override
	public @Nullable String mappingId() {
		return transitiveOnly ? mod.getId() : null;
	}

	@Override
	public String getSortKey() {
		return mod.getId() + ":" + path;
	}

	@Override
	public void read(ClassTweakerVisitor visitor, LazyCloseable<TinyRemapper> remapper, MappingsNamespace productionNamespace) throws IOException {
		if (transitiveOnly) {
			// Filter for only transitive rules
			visitor = new TransitiveOnlyFilter(visitor);
		}

		final byte[] data = readRaw();
		final ClassTweakerReader.Header header = ClassTweakerReader.readHeader(data);

		if (!header.getNamespace().equals(MappingsNamespace.NAMED.toString())) {
			// Remap the AW if needed
			visitor = getRemapper(visitor, remapper.get(), productionNamespace);
		}

		var reader = ClassTweakerReader.create(visitor);
		reader.read(data, mod.getId());
	}

	@Override
	public void readOfficial(ClassTweakerVisitor visitor) throws IOException {
		if (transitiveOnly) {
			// Filter for only transitive rules
			visitor = new TransitiveOnlyFilter(visitor);
		}

		final byte[] data = readRaw();
		final ClassTweakerReader.Header header = ClassTweakerReader.readHeader(data);

		if (!header.getNamespace().equals(MappingsNamespace.OFFICIAL.toString())) {
			throw new IOException("Expected official namespace for access widener entry, found: " + header.getNamespace());
		}

		var reader = ClassTweakerReader.create(visitor);
		reader.read(data, mod.getId());
	}

	private static ClassTweakerRemapperVisitor getRemapper(ClassTweakerVisitor visitor, TinyRemapper tinyRemapper, MappingsNamespace productionNamespace) {
		return new ClassTweakerRemapperVisitor(
				visitor,
				tinyRemapper.getEnvironment().getRemapper(),
				productionNamespace.toString(),
				MappingsNamespace.NAMED.toString()
		);
	}

	private byte[] readRaw() throws IOException {
		return mod.getSource().read(path);
	}
}
