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

package net.fabricmc.loom.configuration.providers.mappings.file;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.api.mappings.layered.MappingLayer;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.providers.mappings.intermediary.IntermediaryMappingLayer;
import net.fabricmc.loom.util.FileSystemUtil;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingUtil;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.adapter.MappingNsRenamer;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.MappingFormat;

public record FileMappingsLayer(
		Path path, @Nullable String mappingPath,
		String fallbackSourceNamespace, String fallbackTargetNamespace,
		@Nullable MappingFormat mappingFormat,
		MappingsNamespace sourceNamespace
) implements MappingLayer {
	@Override
	public void visit(MappingVisitor mappingVisitor) throws IOException {
		// Bare file
		if (mappingPath == null) {
			visit(path, mappingVisitor);
		} else {
			try (FileSystemUtil.Delegate fileSystem = FileSystemUtil.getJarFileSystem(path)) {
				visit(fileSystem.get().getPath(mappingPath), mappingVisitor);
			}
		}
	}

	private void visit(Path path, MappingVisitor mappingVisitor) throws IOException {
		MappingSourceNsSwitch nsSwitch = new MappingSourceNsSwitch(mappingVisitor, sourceNamespace.toString());
		MappingVisitor next = nsSwitch;

		// Replace the default fallback namespaces with
		// our fallback namespaces if potentially needed.
		if (mappingFormat == null || !mappingFormat.hasNamespaces) {
			Map<String, String> fallbackNamespaceReplacements = Map.of(
					MappingUtil.NS_SOURCE_FALLBACK, fallbackSourceNamespace,
					MappingUtil.NS_TARGET_FALLBACK, fallbackTargetNamespace
			);
			next = new MappingNsRenamer(nsSwitch, fallbackNamespaceReplacements);
		}

		MappingReader.read(path, mappingFormat, next);
	}

	@Override
	public MappingsNamespace getSourceNamespace() {
		return sourceNamespace;
	}

	@Override
	public List<Class<? extends MappingLayer>> dependsOn() {
		return List.of(IntermediaryMappingLayer.class);
	}
}
