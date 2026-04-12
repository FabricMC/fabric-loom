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
import org.jspecify.annotations.Nullable;

import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;

public sealed interface UnpickMetadata permits UnpickMetadata.V1, UnpickMetadata.V2 {
	String UNPICK_METADATA_PATH = "extras/unpick.json";
	String UNPICK_DEFINITIONS_PATH = "extras/definitions.unpick";

	/**
	 * @return whether constants are required by the Unpick definitions
	 */
	boolean hasConstants();

	/**
	 * @return whether the maven location of the constants is specified by this metadata
	 */
	boolean hasConstantsLocation();

	UnpickMetadata withConstants(String constants);

	default UnpickMetadata withoutConstants() {
		return withConstants(null);
	}

	/**
	 * @param unpickGroup Deprecated, always uses the version of unpick loom depends on.
	 * @param unpickVersion Deprecated, always uses the version of unpick loom depends on.
	 */
	record V1(@Deprecated String unpickGroup, @Deprecated String unpickVersion) implements UnpickMetadata {
		@Override
		public boolean hasConstants() {
			return true;
		}

		@Override
		public boolean hasConstantsLocation() {
			return false;
		}

		@Override
		public UnpickMetadata withConstants(String constants) {
			// all v1 data is deprecated and ignored by Loom
			// and only v2 format allows setting the constants location
			return new UnpickMetadata.V2(MappingsNamespace.NAMED.toString(), constants);
		}
	}

	/**
	 * Unpick metadata v2.
	 *
	 * @param namespace the mapping namespace of the unpick definitions
	 * @param constants An optional maven notation of the constants jar.
	 */
	record V2(String namespace, @Nullable String constants) implements UnpickMetadata {
		@Override
		public boolean hasConstants() {
			return constants != null;
		}

		@Override
		public boolean hasConstantsLocation() {
			return true;
		}

		@Override
		public UnpickMetadata withConstants(String constants) {
			return new UnpickMetadata.V2(namespace, constants);
		}
	}

	static String toJson(UnpickMetadata metadata) {
		JsonObject json = LoomGradlePlugin.GSON.toJsonTree(metadata).getAsJsonObject();

		int version = switch (metadata) {
		case UnpickMetadata.V1 v1 -> 1;
		case UnpickMetadata.V2 v2 -> 2;
		};
		json.addProperty("version", version);

		return json.toString();
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
				getString(jsonObject, "unpickGroup"),
				getString(jsonObject, "unpickVersion")
			);
		}
		case 2 -> {
			return new V2(
				getString(jsonObject, "namespace"),
				getOptionalString(jsonObject, "constants")
			);
		}
		default -> throw new UnsupportedOperationException("Unsupported unpick metadata version: %s. Please update loom.".formatted(version));
		}
	}

	private static String getString(JsonObject jsonObject, String key) {
		if (!jsonObject.has(key)) {
			throw new UnsupportedOperationException("Missing unpick metadata %s".formatted(key));
		}

		return jsonObject.get(key).getAsString();
	}

	@Nullable
	private static String getOptionalString(JsonObject jsonObject, String key) {
		return jsonObject.has(key) ? jsonObject.get(key).getAsString() : null;
	}
}
