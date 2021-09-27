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

package net.fabricmc.loom.configuration.providers.mappings.extras.signatures;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.jetbrains.annotations.ApiStatus;

import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.api.mappings.layered.MappingLayer;
import net.fabricmc.mappingio.MappingVisitor;

@ApiStatus.Experimental
public record SignatureFixesLayerImpl(Path mappingsFile) implements MappingLayer, SignatureFixesLayer {
	private static final String SIGNATURE_FIXES_PATH = "extras/record_signatures.json";

	@Override
	public void visit(MappingVisitor mappingVisitor) throws IOException {
		// Nothing to do here
	}

	@Override
	public Map<String, String> getSignatureFixes() {
		try (var zipFile = new ZipFile(mappingsFile().toFile())) {
			ZipEntry zipFileEntry = zipFile.getEntry(SIGNATURE_FIXES_PATH);
			Objects.requireNonNull(zipFileEntry, "Could not find %s in file".formatted(SIGNATURE_FIXES_PATH));

			try (var reader = new InputStreamReader(zipFile.getInputStream(zipFileEntry))) {
				//noinspection unchecked
				return LoomGradlePlugin.OBJECT_MAPPER.readValue(reader, Map.class);
			}
		} catch (IOException e) {
			throw new RuntimeException("Failed to extract signature fixes", e);
		}
	}
}
