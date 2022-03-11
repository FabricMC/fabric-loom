/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2021 FabricMC
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

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

import net.fabricmc.loom.api.LoomGradleExtensionAPI;
import net.fabricmc.loom.configuration.ModMetadataHelper;

public final class ModUtils {
	private ModUtils() {
	}

	public static boolean isMod(LoomGradleExtensionAPI ext, File input) {
		return ZipUtils.containsAny(input.toPath(), ext.getModMetadataHelpers().get().keySet());
	}

	public static boolean isMod(Map<String, ModMetadataHelper> helpers, File input) {
		return ZipUtils.containsAny(input.toPath(), helpers.keySet());
	}

	/**
	 * @throws UnsupportedOperationException if the jar file has more than one kind of metadata, or the metadata that is found cannot be read.
	 */
	public static ModMetadataHelper.Metadata readMetadataFromJar(LoomGradleExtensionAPI ext, File jar) {
		return ModUtils.readMetadataFromJar(ext.getModMetadataHelpers().get(), jar);
	}

	/**
	 * @throws UnsupportedOperationException if the jar file has more than one kind of metadata, or the metadata that is found cannot be read.
	 */
	public static ModMetadataHelper.Metadata readMetadataFromJar(Map<String, ModMetadataHelper> helpers, File jar) {
		try (var zip = new ZipFile(jar)) {
			List<String> entries = helpers.keySet()
					.stream()
					.filter(name -> zip.getEntry(name) != null)
					.toList();

			if (entries.isEmpty()) {
				return null;
			}

			if (entries.size() > 1) {
				throw new UnsupportedOperationException("Cannot read jars with more than one kind of metadata.");
			}

			String fileName = entries.get(0);

			try (InputStreamReader reader = new InputStreamReader(zip.getInputStream(zip.getEntry(fileName)))) {
				return helpers.get(fileName).createMetadata(reader);
			}
		} catch (IOException e) {
			throw new UnsupportedOperationException("Cannot read metadata in the jar.", e);
		}
	}
}
