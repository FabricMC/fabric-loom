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

package net.fabricmc.loom.configuration.providers.mappings.utils;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.api.mappings.layered.MappingContext;
import net.fabricmc.loom.api.mappings.layered.spec.FileSpec;
import net.fabricmc.loom.util.download.DownloadException;

public record URLFileSpec(String url) implements FileSpec {
	private static final Logger LOGGER = LoggerFactory.getLogger(URLFileSpec.class);
	@Override
	public Path get(MappingContext context) {
		try {
			Path output = context.workingDirectory(String.format(Locale.ENGLISH, "%d.URLFileSpec", Objects.hash(url)));
			LOGGER.info("Downloading {} to {}", url, output);
			context.download(url)
					.defaultCache()
					.downloadPath(output);
			return output;
		} catch (DownloadException e) {
			throw new UncheckedIOException("Failed to download: " + url, e);
		}
	}

	@Override
	public int hashCode() {
		// URL performs DNS requests if you .hashCode it (:
		return Objects.hash(url.toString());
	}
}
