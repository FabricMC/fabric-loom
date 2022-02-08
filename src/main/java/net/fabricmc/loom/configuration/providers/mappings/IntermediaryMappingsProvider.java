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

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.common.net.UrlEscapers;
import org.gradle.api.provider.Property;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.api.mappings.intermediate.IntermediateMappingsProvider;
import net.fabricmc.loom.util.DownloadUtil;

public abstract class IntermediaryMappingsProvider extends IntermediateMappingsProvider {
	private static final Logger LOGGER = LoggerFactory.getLogger(IntermediateMappingsProvider.class);

	public abstract Property<String> getIntermediaryUrl();

	@Override
	public void provide(Path tinyMappings) throws IOException {
		if (Files.exists(tinyMappings) && !LoomGradlePlugin.refreshDeps) {
			return;
		}

		// Download and extract intermediary
		final Path intermediaryJarPath = Files.createTempFile(getName(), ".jar");
		final String encodedMcVersion = UrlEscapers.urlFragmentEscaper().escape(getMinecraftVersion().get());
		final URL url = new URL(getIntermediaryUrl().get().formatted(encodedMcVersion));

		LOGGER.info("Downloading intermediary from {}", url);

		Files.deleteIfExists(tinyMappings);
		Files.deleteIfExists(intermediaryJarPath);

		DownloadUtil.downloadIfChanged(url, intermediaryJarPath.toFile(), LOGGER);
		MappingsProviderImpl.extractMappings(intermediaryJarPath, tinyMappings);
	}

	@Override
	public @NotNull String getName() {
		return "intermediary-v2";
	}
}
