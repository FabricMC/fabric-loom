/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2023 FabricMC
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

package net.fabricmc.loom.configuration.mods;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.util.FileSystemUtil;
import net.fabricmc.loom.util.fmj.FabricModJson;
import net.fabricmc.loom.util.fmj.FabricModJsonFactory;

public final class MixinDetector {
	public static boolean hasMixinsWithoutRefmap(Path modJar) throws IOException {
		try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(modJar)) {
			final List<String> mixinConfigs = getMixinConfigs(modJar);

			if (!mixinConfigs.isEmpty()) {
				for (String mixinConfig : mixinConfigs) {
					final Path configPath = fs.getPath(mixinConfig);
					if (Files.notExists(configPath)) continue;

					try (BufferedReader reader = Files.newBufferedReader(configPath)) {
						final JsonObject json = LoomGradlePlugin.GSON.fromJson(reader, JsonObject.class);

						if (!json.has("refmap")) {
							// We found a mixin config with no refmap, exit the loop.
							return true;
						}
					} catch (JsonParseException e) {
						throw new RuntimeException("Could not parse mixin config %s from jar %s".formatted(mixinConfig, modJar.toAbsolutePath()), e);
					}
				}
			}

			return false;
		}
	}

	private static List<String> getMixinConfigs(Path modJar) {
		// Nullable because we don't care here if we can't read it.
		// We can just assume there are no mixins.
		final FabricModJson fabricModJson = FabricModJsonFactory.createFromZipNullable(modJar);
		return fabricModJson != null ? fabricModJson.getMixinConfigurations() : List.of();
	}
}
