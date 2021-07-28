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

package net.fabricmc.loom.configuration.providers.mappings.tiny;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.configuration.providers.mappings.MappingLayer;
import net.fabricmc.loom.configuration.providers.mappings.MappingNamespace;
import net.fabricmc.loom.configuration.providers.mappings.intermediary.IntermediaryMappingLayer;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.Tiny2Reader;

public record TinyMappingLayer(File mappingFile, @Nullable String mappingPath) implements MappingLayer {
	@Override
	public void visit(MappingVisitor mappingVisitor) throws IOException {
		if (mappingPath == null) { // bare file
			try (Reader reader = new FileReader(mappingFile)) {
				read(mappingVisitor, reader);
			}
		} else { // assume a zip/jar
			try (ZipFile zip = new ZipFile(mappingFile)) {
				ZipEntry entry = zip.getEntry(mappingPath);

				if (entry == null) {
					throw new IOException("Could not find mappings inside " + mappingFile);
				}

				try (InputStream in = zip.getInputStream(entry); Reader reader = new InputStreamReader(in)) {
					read(mappingVisitor, reader);
				}
			}
		}
	}

	private void read(MappingVisitor visitor, Reader reader) throws IOException {
		// we might have official -> intermediary, named here - let's reorder just to be safe
		MappingSourceNsSwitch nsSwitch = new MappingSourceNsSwitch(visitor, getSourceNamespace().stringValue());
		DummyNsReplacer nsReplacer = new DummyNsReplacer(nsSwitch);
		MappingReader.read(reader, null, nsReplacer);
	}

	@Override
	public MappingNamespace getSourceNamespace() {
		return MappingNamespace.INTERMEDIARY;
	}

	@Override
	public List<Class<? extends MappingLayer>> dependsOn() {
		return List.of(IntermediaryMappingLayer.class);
	}
}
