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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

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

		return getConditionalConfigs(jsonObject.get("mixins"), ModEnvironment.UNIVERSAL);
	}

	@Override
	public List<String> getClassTweakers(ModEnvironment modEnvironment) {
		if (!jsonObject.has("classTweakers")) {
			return Collections.emptyList();
		}

		return getConditionalConfigs(jsonObject.get("classTweakers"), modEnvironment);
	}

	private List<String> getConditionalConfigs(JsonElement jsonElement, ModEnvironment modEnvironment) {
		final List<String> values = new ArrayList<>();

		if (jsonElement instanceof JsonArray jsonArray) {
			for (JsonElement arrayElement : jsonArray) {
				final String value = readConditionalConfig(arrayElement, modEnvironment);

				if (value != null) {
					values.add(value);
				}
			}
		} else if (jsonElement instanceof JsonPrimitive jsonPrimitive && jsonPrimitive.isString()) {
			final String value = readConditionalConfig(jsonPrimitive, modEnvironment);

			if (value != null) {
				values.add(value);
			}
		} else {
			throw new FabricModJsonUtils.ParseException("Must be a string or array of strings");
		}

		return values;
	}

	@Nullable
	private String readConditionalConfig(JsonElement jsonElement, ModEnvironment modEnvironment) {
		if (jsonElement instanceof JsonPrimitive jsonPrimitive && jsonPrimitive.isString()) {
			return jsonElement.getAsString();
		} else if (jsonElement instanceof JsonObject jsonObject) {
			final String config = FabricModJsonUtils.readString(jsonObject, "config");

			if (!validForEnvironment(jsonObject, modEnvironment)) {
				return null;
			}

			return config;
		} else {
			throw new FabricModJsonUtils.ParseException("Must be a string or an object");
		}
	}

	private boolean validForEnvironment(JsonObject jsonObject, ModEnvironment modEnvironment) {
		if (!jsonObject.has("environment")) {
			// Default enabled for all envs.
			return true;
		}

		if (!(jsonObject.get("environment") instanceof JsonPrimitive jsonPrimitive) || !jsonPrimitive.isString()) {
			throw new FabricModJsonUtils.ParseException("Environment must be a string");
		}

		final String environment = jsonPrimitive.getAsString();

		return switch (environment) {
		case "*" -> true;
		case "client" -> modEnvironment.isClient();
		case "server" -> modEnvironment.isServer();
		default -> throw new FabricModJsonUtils.ParseException("Invalid environment type: " + environment);
		};
	}
}
