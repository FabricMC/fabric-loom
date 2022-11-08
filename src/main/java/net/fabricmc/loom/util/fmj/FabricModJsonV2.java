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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.util.Pair;

@ApiStatus.Experimental
public final class FabricModJsonV2 extends FabricModJson {
	FabricModJsonV2(JsonObject jsonObject, FabricModJsonSource source) {
		super(jsonObject, source);
	}

	@Override
	public int getVersion() {
		return 2;
	}

	@Override
	@Nullable
	public JsonElement getCustom(String key) {
		return FabricModJsonV1.getCustom(jsonObject, key);
	}

	@Override
	public List<String> getMixinConfigurations() {
		if (!jsonObject.has("mixins")) {
			return Collections.emptyList();
		}

		return List.copyOf(getConditionalConfigs(jsonObject.get("mixins")).keySet());
	}

	@Override
	public Map<String, ModEnvironment> getClassTweakers() {
		if (!jsonObject.has("classTweakers")) {
			return Collections.emptyMap();
		}

		return getConditionalConfigs(jsonObject.get("classTweakers"));
	}

	private Map<String, ModEnvironment> getConditionalConfigs(JsonElement jsonElement) {
		final Map<String, ModEnvironment> values = new HashMap<>();

		if (jsonElement instanceof JsonArray jsonArray) {
			for (JsonElement arrayElement : jsonArray) {
				final Pair<String, ModEnvironment> value = readConditionalConfig(arrayElement);

				if (value != null) {
					values.put(value.left(), value.right());
				}
			}
		} else if (jsonElement instanceof JsonPrimitive jsonPrimitive && jsonPrimitive.isString()) {
			final Pair<String, ModEnvironment> value = readConditionalConfig(jsonPrimitive);

			if (value != null) {
				values.put(value.left(), value.right());
			}
		} else {
			throw new FabricModJsonUtils.ParseException("Must be a string or array of strings");
		}

		return values;
	}

	@Nullable
	private Pair<String, ModEnvironment> readConditionalConfig(JsonElement jsonElement) {
		if (jsonElement instanceof JsonPrimitive jsonPrimitive && jsonPrimitive.isString()) {
			return new Pair<>(jsonElement.getAsString(), ModEnvironment.UNIVERSAL);
		} else if (jsonElement instanceof JsonObject jsonObject) {
			final String config = FabricModJsonUtils.readString(jsonObject, "config");
			return new Pair<>(config, getEnvironment(jsonObject));
		} else {
			throw new FabricModJsonUtils.ParseException("Must be a string or an object");
		}
	}

	private ModEnvironment getEnvironment(JsonObject jsonObject) {
		if (!jsonObject.has("environment")) {
			// Default enabled for all envs.
			return ModEnvironment.UNIVERSAL;
		}

		if (!(jsonObject.get("environment") instanceof JsonPrimitive jsonPrimitive) || !jsonPrimitive.isString()) {
			throw new FabricModJsonUtils.ParseException("Environment must be a string");
		}

		final String environment = jsonPrimitive.getAsString();

		return switch (environment) {
		case "*" -> ModEnvironment.UNIVERSAL;
		case "client" -> ModEnvironment.CLIENT;
		case "server" -> ModEnvironment.SERVER;
		default -> throw new FabricModJsonUtils.ParseException("Invalid environment type: " + environment);
		};
	}
}
