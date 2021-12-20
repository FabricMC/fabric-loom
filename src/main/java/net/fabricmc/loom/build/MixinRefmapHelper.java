/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2018-2021 FabricMC
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

package net.fabricmc.loom.build;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.LoomGradlePlugin;

public final class MixinRefmapHelper {
	private MixinRefmapHelper() { }

	private static final String FABRIC_MOD_JSON = "fabric.mod.json";

	@Nullable
	public static JsonObject readFabricModJson(File output) {
		try (ZipFile zip = new ZipFile(output)) {
			ZipEntry entry = zip.getEntry(FABRIC_MOD_JSON);

			if (entry == null) {
				return null;
			}

			try (InputStreamReader reader = new InputStreamReader(zip.getInputStream(entry))) {
				return LoomGradlePlugin.GSON.fromJson(reader, JsonObject.class);
			}
		} catch (IOException e) {
			throw new RuntimeException("Cannot read file fabric.mod.json in the jar.", e);
		}
	}

	@NotNull
	public static Collection<String> getMixinConfigurationFiles(JsonObject fabricModJson) {
		JsonArray mixins = fabricModJson.getAsJsonArray("mixins");

		if (mixins == null) {
			return Collections.emptyList();
		}

		return StreamSupport.stream(mixins.spliterator(), false)
				.map(e -> {
					if (e instanceof JsonPrimitive str) {
						return str.getAsString();
					} else if (e instanceof JsonObject obj) {
						return obj.get("config").getAsString();
					} else {
						throw new RuntimeException("Incorrect fabric.mod.json format");
					}
				}).collect(Collectors.toSet());
	}
}
