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

package net.fabricmc.loom.util.fmj.gen;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

public final class GeneratorUtils {
	private GeneratorUtils() {
	}

	public static void add(JsonObject json, String key, Property<String> property) {
		add(json, key, property, JsonPrimitive::new);
	}

	public static void addRequired(JsonObject json, String key, Property<String> property) {
		addRequired(json, key, property, JsonPrimitive::new);
	}

	public static void addStringOrArray(JsonObject json, String key, ListProperty<String> property) {
		if (property.get().isEmpty()) {
			return;
		}

		add(json, key, property, GeneratorUtils::stringOrArray);
	}

	public static <V> void addSingleOrArray(JsonObject json, String key, ListProperty<V> property, Function<V, JsonElement> converter) {
		if (property.get().isEmpty()) {
			return;
		}

		add(json, key, property, entries -> singleOrArray(entries, converter));
	}

	public static <V> void addArray(JsonObject json, String key, ListProperty<V> property, Function<V, JsonElement> converter) {
		if (property.get().isEmpty()) {
			return;
		}

		add(json, key, property, entries -> array(entries, converter));
	}

	public static <V, P extends Property<V>> void add(JsonObject json, String key, P property, Function<V, JsonElement> converter) {
		if (!property.isPresent()) {
			return;
		}

		json.add(key, converter.apply(property.get()));
	}

	public static <V, P extends Property<V>> void addRequired(JsonObject json, String key, P property, Function<V, JsonElement> converter) {
		property.get(); // Ensure it's present
		add(json, key, property, converter);
	}

	public static <V> void add(JsonObject json, String key, ListProperty<V> property, Function<List<V>, JsonElement> converter) {
		if (property.get().isEmpty()) {
			return;
		}

		json.add(key, converter.apply(property.get()));
	}

	public static <K, V> void add(JsonObject json, String key, MapProperty<K, V> property, Function<Map<K, V>, JsonElement> converter) {
		if (property.get().isEmpty()) {
			return;
		}

		json.add(key, converter.apply(property.get()));
	}

	public static void add(JsonObject json, String key, MapProperty<String, String> property) {
		if (property.get().isEmpty()) {
			return;
		}

		add(json, key, property, GeneratorUtils::map);
	}

	public static JsonElement stringOrArray(List<String> strings) {
		return singleOrArray(strings, JsonPrimitive::new);
	}

	public static <V> JsonElement singleOrArray(List<V> entries, Function<V, JsonElement> converter) {
		if (entries.size() == 1) {
			return converter.apply(entries.getFirst());
		}

		return array(entries, converter);
	}

	public static <V> JsonElement array(List<V> entries, Function<V, JsonElement> converter) {
		JsonArray array = new JsonArray();

		for (V entry : entries) {
			array.add(converter.apply(entry));
		}

		return array;
	}

	public static JsonObject map(Map<String, String> map) {
		JsonObject obj = new JsonObject();

		for (Map.Entry<String, String> entry : map.entrySet()) {
			obj.addProperty(entry.getKey(), entry.getValue());
		}

		return obj;
	}
}
