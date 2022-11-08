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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.Nullable;

@Deprecated
public final class FabricModJsonV0 extends FabricModJson {
	FabricModJsonV0(JsonObject jsonObject, FabricModJsonSource source) {
		super(jsonObject, source);
	}

	@Override
	public int getVersion() {
		return 0;
	}

	@Override
	@Nullable
	public JsonElement getCustom(String key) {
		return null;
	}

	@Override
	public List<String> getMixinConfigurations() {
		final JsonObject mixinsObject = jsonObject.getAsJsonObject("mixins");

		if (mixinsObject == null) {
			return Collections.emptyList();
		}

		final List<String> mixins = new ArrayList<>();

		for (String key : mixinsObject.keySet()) {
			final JsonElement jsonElement = mixinsObject.get(key);

			if (jsonElement instanceof JsonArray jsonArray) {
				for (JsonElement arrayElement : jsonArray) {
					if (arrayElement instanceof JsonPrimitive jsonPrimitive && jsonPrimitive.isString()) {
						mixins.add(jsonPrimitive.getAsString());
					} else {
						throw new FabricModJsonUtils.ParseException("Expected entries in mixin %s to be an array of strings", key);
					}
				}
			} else if (jsonElement instanceof JsonPrimitive jsonPrimitive && jsonPrimitive.isString()) {
				mixins.add(jsonPrimitive.getAsString());
			} else {
				throw new FabricModJsonUtils.ParseException("Expected mixin %s to be a string or an array of strings", key);
			}
		}

		return Collections.unmodifiableList(mixins);
	}

	@Override
	public Map<String, ModEnvironment> getClassTweakers() {
		return Collections.emptyMap();
	}
}
