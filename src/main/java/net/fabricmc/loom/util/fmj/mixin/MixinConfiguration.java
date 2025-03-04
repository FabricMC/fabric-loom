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
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.util.fmj.FabricModJson;
import net.fabricmc.loom.util.fmj.FabricModJsonSource;

public record MixinConfiguration(
		@Nullable MixinRefmap refmap) {
	private static final String REFMAP_KEY = "refmap";

	public static List<MixinConfiguration> fromMod(FabricModJson fabricModJson) throws IOException {
		var configs = new ArrayList<MixinConfiguration>();

		for (String configPath : fabricModJson.getMixinConfigurations()) {
			configs.add(fromMod(configPath, fabricModJson.getSource()));
		}

		return configs;
	}

	private static MixinConfiguration fromMod(String configPath, FabricModJsonSource modSource) throws IOException {
		final String mixinConfigJson = new String(modSource.read(configPath));
		final JsonObject jsonObject = LoomGradlePlugin.GSON.fromJson(mixinConfigJson, JsonObject.class);

		MixinRefmap refmap = null;

		if (jsonObject.has(REFMAP_KEY)) {
			String refmapPath = jsonObject.get(REFMAP_KEY).getAsString();
			refmap = MixinRefmap.fromMod(refmapPath, modSource);
		}

		return new MixinConfiguration(refmap);
	}
}
