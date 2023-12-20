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

package net.fabricmc.loom.task.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.jar.Manifest;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.configuration.mods.ArtifactMetadata;
import net.fabricmc.loom.util.AttributeHelper;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.ZipUtils;

public record RemapClasspathEntry(Path path, @Nullable ArtifactMetadata.MixinRemapType mixinRemapType) {
	private static final String ATTRIBUTE_KEY = "LoomUsesStaticMixinRemapping";

	public static RemapClasspathEntry create(Path path) {
		if (!Files.isRegularFile(path)) {
			return new RemapClasspathEntry(path, null);
		}

		try {
			ArtifactMetadata.MixinRemapType remapType = AttributeHelper.readAttribute(path, ATTRIBUTE_KEY)
					.map(RemapClasspathEntry::remapTypeFromString)
					.orElse(null);

			if (remapType == null) {
				remapType = readRemapType(path);
			}

			return new RemapClasspathEntry(path, remapType);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to read classpath jar: " + path, e);
		}
	}

	public boolean usesStaticMixinRemapping() {
		return mixinRemapType == ArtifactMetadata.MixinRemapType.STATIC;
	}

	@Nullable
	private static ArtifactMetadata.MixinRemapType readRemapType(Path path) throws IOException {
		byte[] manifestBytes = ZipUtils.unpackNullable(path, Constants.Manifest.PATH);

		if (manifestBytes == null) {
			return null;
		}

		final var manifest = new Manifest(new ByteArrayInputStream(manifestBytes));
		final String mixinRemapType = manifest.getMainAttributes().getValue(Constants.Manifest.MIXIN_REMAP_TYPE);

		if (mixinRemapType != null) {
			ArtifactMetadata.MixinRemapType remapType = remapTypeFromString(mixinRemapType);
			AttributeHelper.writeAttribute(path, ATTRIBUTE_KEY, remapType.name());
			return remapType;
		}

		return null;
	}

	private static ArtifactMetadata.MixinRemapType remapTypeFromString(String str) {
		try {
			return ArtifactMetadata.MixinRemapType.valueOf(str.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new IllegalStateException("Unknown mixin remap type: " + str);
		}
	}
}
