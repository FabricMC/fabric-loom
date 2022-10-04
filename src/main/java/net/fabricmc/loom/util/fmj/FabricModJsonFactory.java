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

import static net.fabricmc.loom.util.fmj.FabricModJsonUtils.readInt;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.JsonObject;
import org.gradle.api.tasks.SourceSet;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.util.ZipUtils;
import net.fabricmc.loom.util.gradle.SourceSetHelper;

public final class FabricModJsonFactory {
	private static final String FABRIC_MOD_JSON = "fabric.mod.json";

	private FabricModJsonFactory() {
	}

	@VisibleForTesting
	public static FabricModJson create(JsonObject jsonObject, FabricModJsonSource source) {
		int schemaVersion = 0;

		if (jsonObject.has("schemaVersion")) {
			// V0 had no schemaVersion key.
			schemaVersion = readInt(jsonObject, "schemaVersion");
		}

		return switch (schemaVersion) {
		case 0 -> new FabricModJsonV0(jsonObject, source);
		case 1 -> new FabricModJsonV1(jsonObject, source);
		case 2 -> new FabricModJsonV2(jsonObject, source);
		default -> throw new UnsupportedOperationException(String.format("This version of fabric-loom doesn't support the newer fabric.mod.json schema version of (%s) Please update fabric-loom to be able to read this.", schemaVersion));
		};
	}

	public static FabricModJson createFromZip(Path zipPath) {
		try {
			return create(ZipUtils.unpackGson(zipPath, FABRIC_MOD_JSON, JsonObject.class), new FabricModJsonSource.ZipSource(zipPath));
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to read fabric.mod.json file in zip: " + zipPath, e);
		}
	}

	@Nullable
	public static FabricModJson createFromZipNullable(Path zipPath) throws IOException {
		JsonObject jsonObject = ZipUtils.unpackGsonNullable(zipPath, FABRIC_MOD_JSON, JsonObject.class);

		if (jsonObject == null) {
			return null;
		}

		return create(jsonObject, new FabricModJsonSource.ZipSource(zipPath));
	}

	public static FabricModJson createFromDirectory(Path directory) throws IOException {
		final Path path = directory.resolve(FABRIC_MOD_JSON);

		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			return create(LoomGradlePlugin.GSON.fromJson(reader, JsonObject.class), new FabricModJsonSource.DirectorySource(directory));
		}
	}

	@Nullable
	public static FabricModJson createFromSourceSetsNullable(SourceSet... sourceSets) throws IOException {
		final File file = SourceSetHelper.findFirstFileInResource(FABRIC_MOD_JSON, sourceSets);

		if (file == null) {
			return null;
		}

		try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
			return create(LoomGradlePlugin.GSON.fromJson(reader, JsonObject.class), new FabricModJsonSource.SourceSetSource(sourceSets));
		}
	}

	public static boolean isModJar(File file) {
		return isModJar(file.toPath());
	}

	public static boolean isModJar(Path input) {
		return ZipUtils.contains(input, FABRIC_MOD_JSON);
	}
}
