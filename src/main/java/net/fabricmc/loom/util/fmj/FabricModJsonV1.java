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

import static net.fabricmc.loom.util.fmj.FabricModJsonUtils.readString;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.Nullable;

public final class FabricModJsonV1 extends FabricModJson {
	FabricModJsonV1(JsonObject jsonObject, FabricModJsonSource source) {
		super(jsonObject, source);
	}

	@Override
	public int getVersion() {
		return 1;
	}

	@Override
	@Nullable
	public JsonElement getCustom(String key) {
		return getCustom(jsonObject, key);
	}

	static JsonElement getCustom(JsonObject jsonObject, String key) {
		if (!jsonObject.has("custom")) {
			return null;
		}

		final JsonObject custom = jsonObject.getAsJsonObject("custom");

		if (!custom.has(key)) {
			return null;
		}

		return custom.get(key);
	}

	@Override
	public List<String> getMixinConfigurations() {
		final JsonArray mixinArray = jsonObject.getAsJsonArray("mixins");

		if (mixinArray == null) {
			return Collections.emptyList();
		}

		return StreamSupport.stream(mixinArray.spliterator(), false)
				.map(FabricModJsonV1::readMixinElement)
				.collect(Collectors.toList());
	}

	private static String readMixinElement(JsonElement jsonElement) {
		if (jsonElement instanceof JsonPrimitive str) {
			return str.getAsString();
		} else if (jsonElement instanceof JsonObject obj) {
			return obj.get("config").getAsString();
		} else {
			throw new FabricModJsonUtils.ParseException("Expected mixin element to be an object or string");
		}
	}

	@Override
	public Map<String, ModEnvironment> getClassTweakers() {
		if (!jsonObject.has("accessWidener")) {
			return Collections.emptyMap();
		}

		return Map.of(readString(jsonObject, "accessWidener"), ModEnvironment.UNIVERSAL);
	}
}
