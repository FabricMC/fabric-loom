/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 FabricMC
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

package net.fabricmc.loom.configuration.providers.minecraft.verify;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;

import net.fabricmc.loom.LoomGradlePlugin;

/**
 * The know versions keep track of the versions that are signed using SHA1 or not signature at all.
 * The maps are the Minecraft version to sha256 hash of the jar file.
 */
public record KnownVersions(
		Map<String, String> client,
		Map<String, String> server) {
	public static final Supplier<KnownVersions> INSTANCE = Suppliers.memoize(KnownVersions::load);

	private static KnownVersions load() {
		try (InputStream is = KnownVersions.class.getClassLoader().getResourceAsStream("certs/known_versions.json");
				Reader reader = new InputStreamReader(Objects.requireNonNull(is))) {
			return LoomGradlePlugin.GSON.fromJson(reader, KnownVersions.class);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to load known versions", e);
		}
	}
}
