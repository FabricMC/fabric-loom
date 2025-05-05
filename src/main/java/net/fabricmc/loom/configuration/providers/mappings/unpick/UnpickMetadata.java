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

package net.fabricmc.loom.configuration.providers.mappings.unpick;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.JsonObject;

import net.fabricmc.loom.LoomGradlePlugin;

public sealed interface UnpickMetadata permits UnpickMetadata.V1 {
	String UNPICK_METADATA_PATH = "extras/unpick.json";
	String UNPICK_DEFINITIONS_PATH = "extras/definitions.unpick";

	boolean hasConstants();

	record V1(String unpickGroup, String unpickVersion) implements UnpickMetadata {
		@Override
		public boolean hasConstants() {
			return true;
		}
	}

	static UnpickMetadata parse(Path path) throws IOException {
		JsonObject jsonObject = LoomGradlePlugin.GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), JsonObject.class);

		if (!jsonObject.has("version")) {
			throw new UnsupportedOperationException("Missing unpick metadata version");
		}

		int version = jsonObject.get("version").getAsInt();

		switch (version) {
		case 1 -> {
			return new V1(
					jsonObject.get("unpickGroup").getAsString(),
					jsonObject.get("unpickVersion").getAsString()
			);
		}
		default -> throw new UnsupportedOperationException("Unsupported unpick metadata version: %s. Please update loom.".formatted(version));
		}
	}
}
