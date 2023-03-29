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

package net.fabricmc.loom.configuration.providers.mappings;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import com.google.common.net.UrlEscapers;
import org.gradle.api.provider.Property;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.api.mappings.intermediate.IntermediateMappingsProvider;
import net.fabricmc.loom.util.ZipUtils;

public abstract class IntermediaryMappingsProvider extends IntermediateMappingsProvider {
	private static final Logger LOGGER = LoggerFactory.getLogger(IntermediateMappingsProvider.class);

	public abstract Property<String> getIntermediaryUrl();

	public abstract Property<Boolean> getRefreshDeps();

	@Override
	public void provide(Path tinyMappings) throws IOException {
		if (Files.exists(tinyMappings) && !getRefreshDeps().get()) {
			return;
		}

		final Path intermediaryJarPath = getIntermediaryPath();

		Files.deleteIfExists(tinyMappings);
		MappingConfiguration.extractMappings(intermediaryJarPath, tinyMappings);
	}

	@Override
	public Map<String, String> getMetadata() throws IOException {
		Path intermediaryJarPath = getIntermediaryPath();

		byte[] manifestBytes = ZipUtils.unpackNullable(intermediaryJarPath, "META-INF/MANIFEST.MF");

		if (manifestBytes == null) {
			return Collections.emptyMap();
		}

		Map<String, String> metadata = new HashMap<>();

		try (InputStream is = new ByteArrayInputStream(manifestBytes)) {
			final Manifest manifest = new Manifest(is);
			final Attributes mainAttributes = manifest.getMainAttributes();

			for (Object key : mainAttributes.keySet()) {
				metadata.put(key.toString(), mainAttributes.getValue(key.toString()));
			}
		}

		return metadata;
	}

	private Path getIntermediaryPath() throws IOException {
		// Download and extract intermediary
		final Path intermediaryJarPath = Files.createTempFile(getName(), ".jar");
		final String encodedMcVersion = UrlEscapers.urlFragmentEscaper().escape(getMinecraftVersion().get());
		final String url = getIntermediaryUrl().get().formatted(encodedMcVersion);

		LOGGER.info("Downloading intermediary from {}", url);

		Files.deleteIfExists(intermediaryJarPath);

		getDownloader().get().apply(url)
				.defaultCache()
				.downloadPath(intermediaryJarPath);

		return intermediaryJarPath;
	}

	@Override
	public @NotNull String getName() {
		return "intermediary-v2";
	}
}
