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

package net.fabricmc.loom.util.fmj.mixin;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.util.fmj.FabricModJsonSource;

public record MixinRefmap(
		String refmapPath,
		MixinMappingData mappings,
		Map<NamespacePair, MixinMappingData> data) {
	private static final String MAPPINGS_KEY = "mappings";
	private static final String DATA_KEY = "data";

	public static MixinRefmap fromMod(String refmapPath, FabricModJsonSource modSource) throws IOException {
		String refmapJson = new String(modSource.read(refmapPath));
		JsonObject jsonObject = LoomGradlePlugin.GSON.fromJson(refmapJson, JsonObject.class);

		MixinMappingData mappings = MixinMappingData.EMPTY;
		var data = new HashMap<NamespacePair, MixinMappingData>();

		if (jsonObject.has(MAPPINGS_KEY)) {
			mappings = MixinMappingData.parse(jsonObject.getAsJsonObject(MAPPINGS_KEY));
		}

		if (jsonObject.has(DATA_KEY)) {
			JsonObject dataJson = jsonObject.getAsJsonObject(DATA_KEY);

			for (Map.Entry<String, JsonElement> entry : dataJson.entrySet()) {
				String namespaces = entry.getKey();
				JsonObject namespacedData = entry.getValue().getAsJsonObject();
				data.put(NamespacePair.parseString(namespaces), MixinMappingData.parse(namespacedData));
			}
		}

		return new MixinRefmap(refmapPath, mappings, data);
	}

	public MixinMappingData getData(NamespacePair namespaces) {
		MixinMappingData data = this.data.get(namespaces);

		if (data != null) {
			return data;
		}

		// TODO: log warning?
		return mappings;
	}

	public record NamespacePair(
			String from,
			String to) {
		private static NamespacePair parseString(String string) {
			final String[] parts = string.split(":");

			if (parts.length != 2) {
				throw new IllegalArgumentException("Invalid namespace pair: " + string);
			}

			return new NamespacePair(parts[0], parts[1]);
		}
	}

	// Mixin class name -> ReferenceMappingData
	public record MixinMappingData(
			Map<String, ReferenceMappingData> data) {
		private static final MixinMappingData EMPTY = new MixinMappingData(Map.of());

		private static MixinMappingData parse(JsonObject jsonObject) {
			var data = new HashMap<String, ReferenceMappingData>();

			for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
				String mixinName = entry.getKey();
				JsonObject mappings = entry.getValue().getAsJsonObject();
				data.put(mixinName, ReferenceMappingData.parse(mappings));
			}

			return new MixinMappingData(data);
		}

		public String remap(String mixinName, String reference) {
			ReferenceMappingData referenceMappingData = data.get(mixinName);

			if (referenceMappingData == null) {
				return reference;
			}

			return referenceMappingData.remap(reference);
		}
	}

	public record ReferenceMappingData(
			Map<String, String> mappings) {
		private static ReferenceMappingData parse(JsonObject jsonObject) {
			var mappings = new HashMap<String, String>();

			for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
				mappings.put(entry.getKey(), entry.getValue().getAsString());
			}

			return new ReferenceMappingData(mappings);
		}

		public String remap(String reference) {
			return mappings.getOrDefault(reference, reference);
		}
	}
}
