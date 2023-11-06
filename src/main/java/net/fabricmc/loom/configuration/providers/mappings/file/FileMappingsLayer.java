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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.api.mappings.layered.MappingLayer;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.providers.mappings.extras.unpick.UnpickLayer;
import net.fabricmc.loom.configuration.providers.mappings.intermediary.IntermediaryMappingLayer;
import net.fabricmc.loom.util.FileSystemUtil;
import net.fabricmc.loom.util.ZipUtils;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingUtil;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.adapter.MappingNsRenamer;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.MappingFormat;

public record FileMappingsLayer(
		Path path, String mappingPath,
		String fallbackSourceNamespace, String fallbackTargetNamespace,
		boolean enigma, // Enigma cannot be automatically detected since it's stored in a directory.
		boolean unpick,
		String mergeNamespace
) implements MappingLayer, UnpickLayer {
	private static final String UNPICK_METADATA_PATH = "extras/unpick.json";
	private static final String UNPICK_DEFINITIONS_PATH = "extras/definitions.unpick";

	@Override
	public void visit(MappingVisitor mappingVisitor) throws IOException {
		// Bare file
		if (!ZipUtils.isZip(path)) {
			visit(path, mappingVisitor);
		} else {
			try (FileSystemUtil.Delegate fileSystem = FileSystemUtil.getJarFileSystem(path)) {
				visit(fileSystem.get().getPath(mappingPath), mappingVisitor);
			}
		}
	}

	private void visit(Path path, MappingVisitor mappingVisitor) throws IOException {
		MappingSourceNsSwitch nsSwitch = new MappingSourceNsSwitch(mappingVisitor, mergeNamespace.toString());

		// Replace the default fallback namespaces with
		// our fallback namespaces if potentially needed.
		Map<String, String> fallbackNamespaceReplacements = Map.of(
				MappingUtil.NS_SOURCE_FALLBACK, fallbackSourceNamespace,
				MappingUtil.NS_TARGET_FALLBACK, fallbackTargetNamespace
		);
		MappingNsRenamer renamer = new MappingNsRenamer(nsSwitch, fallbackNamespaceReplacements);

		MappingReader.read(path, enigma ? MappingFormat.ENIGMA_DIR : null, renamer);
	}

	@Override
	public MappingsNamespace getSourceNamespace() {
		return MappingsNamespace.of(mergeNamespace);
	}

	@Override
	public List<Class<? extends MappingLayer>> dependsOn() {
		return List.of(IntermediaryMappingLayer.class);
	}

	@Override
	public @Nullable UnpickData getUnpickData() throws IOException {
		if (!unpick) {
			return null;
		}

		if (!ZipUtils.isZip(path)) {
			throw new UnsupportedOperationException("Unpick is only supported for zip file mapping layers.");
		}

		try (FileSystemUtil.Delegate fileSystem = FileSystemUtil.getJarFileSystem(path)) {
			final Path unpickMetadata = fileSystem.get().getPath(UNPICK_METADATA_PATH);
			final Path unpickDefinitions = fileSystem.get().getPath(UNPICK_DEFINITIONS_PATH);

			if (!Files.exists(unpickMetadata)) {
				// No unpick in this zip
				return null;
			}

			return UnpickData.read(unpickMetadata, unpickDefinitions);
		}
	}
}
