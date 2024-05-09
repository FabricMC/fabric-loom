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

package net.fabricmc.loom.util.fmj;

import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public final class FabricModJsonUtils {
	private FabricModJsonUtils() {
	}

	public static String readString(JsonObject jsonObject, String key) {
		final JsonElement element = getElement(jsonObject, key);
		ensurePrimitive(element, JsonPrimitive::isString, key);

		return element.getAsString();
	}

	public static int readInt(JsonObject jsonObject, String key) {
		final JsonElement element = getElement(jsonObject, key);
		ensurePrimitive(element, JsonPrimitive::isNumber, key);

		return element.getAsInt();
	}

	public static JsonObject getJsonObject(JsonObject jsonObject, String key) {
		final JsonElement element = getElement(jsonObject, key);

		if (!element.isJsonObject()) {
			throw new ParseException("Unexpected json object type for key (%s)", key);
		}

		return element.getAsJsonObject();
	}

	// Ensure that the schemaVersion json entry, is first in the json file
	// This exercises an optimisation here: https://github.com/FabricMC/fabric-loader/blob/d69cb72d26497e3f387cf46f9b24340b402a4644/src/main/java/net/fabricmc/loader/impl/metadata/ModMetadataParser.java#L62
	public static JsonObject optimizeFmj(JsonObject json) {
		if (!json.has("schemaVersion")) {
			// No schemaVersion, something will explode later?!
			return json;
		}

		// Create a new json object with the schemaVersion first
		var out = new JsonObject();
		out.add("schemaVersion", json.get("schemaVersion"));

		for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
			if (entry.getKey().equals("schemaVersion")) {
				continue;
			}

			// Add all other entries
			out.add(entry.getKey(), entry.getValue());
		}

		return out;
	}

	private static JsonElement getElement(JsonObject jsonObject, String key) {
		final JsonElement element = jsonObject.get(key);

		if (element == null) {
			throw new ParseException("Unable to find json element for key (%s)", key);
		}

		return element;
	}

	private static void ensurePrimitive(JsonElement jsonElement, Predicate<JsonPrimitive> predicate, String key) {
		if (!jsonElement.isJsonPrimitive() || !predicate.test(jsonElement.getAsJsonPrimitive())) {
			throw new ParseException("Unexpected primitive type for key (%s)", key);
		}
	}

	public static class ParseException extends RuntimeException {
		public ParseException(String message, Object... args) {
			super(String.format(Locale.ROOT, message, args));
		}
	}
}
