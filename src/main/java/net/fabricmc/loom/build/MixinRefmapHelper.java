/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2018-2021 FabricMC
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

package net.fabricmc.loom.build;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.gradle.api.Project;
import org.jetbrains.annotations.NotNull;
import org.zeroturnaround.zip.ZipUtil;
import org.zeroturnaround.zip.transform.StringZipEntryTransformer;
import org.zeroturnaround.zip.transform.ZipEntryTransformerEntry;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.extension.MixinApExtension;

public final class MixinRefmapHelper {
	private MixinRefmapHelper() { }

	private static final String FABRIC_MOD_JSON = "fabric.mod.json";

	public static boolean addRefmapName(Project project, Path outputPath) {
		try {
			MixinApExtension mixin = LoomGradleExtension.get(project).getMixin();
			File output = outputPath.toFile();

			Collection<String> allMixinConfigs = getMixinConfigurationFiles(readFabricModJson(output));

			return mixin.getMixinSourceSetsStream().map(sourceSet -> {
				MixinApExtension.MixinInformationContainer container = Objects.requireNonNull(
						MixinApExtension.getMixinInformationContainer(sourceSet)
				);

				Stream<String> mixinConfigs = sourceSet.getResources()
						.matching(container.mixinConfigPattern())
						.getFiles()
						.stream()
						.map(File::getName)
						.filter(allMixinConfigs::contains);

				String refmapName = container.refmapNameProvider().get();

				return ZipUtil.transformEntries(output, mixinConfigs.map(f -> new ZipEntryTransformerEntry(f, new StringZipEntryTransformer("UTF-8") {
					@Override
					protected String transform(ZipEntry zipEntry, String input) {
						JsonObject json = LoomGradlePlugin.GSON.fromJson(input, JsonObject.class);

						if (!json.has("refmap")) {
							json.addProperty("refmap", refmapName);
						}

						return LoomGradlePlugin.GSON.toJson(json);
					}
				})).toArray(ZipEntryTransformerEntry[]::new));
			}).reduce(false, Boolean::logicalOr);
		} catch (Exception e) {
			project.getLogger().error(e.getMessage());
			return false;
		}
	}

	@NotNull
	private static JsonObject readFabricModJson(File output) {
		try (ZipFile zip = new ZipFile(output)) {
			ZipEntry entry = zip.getEntry(FABRIC_MOD_JSON);

			try (InputStreamReader reader = new InputStreamReader(zip.getInputStream(entry))) {
				return LoomGradlePlugin.GSON.fromJson(reader, JsonObject.class);
			}
		} catch (IOException e) {
			throw new RuntimeException("Cannot read file fabric.mod.json in the output jar.", e);
		}
	}

	@NotNull
	private static Collection<String> getMixinConfigurationFiles(JsonObject fabricModJson) {
		JsonArray mixins = fabricModJson.getAsJsonArray("mixins");

		if (mixins == null) {
			return Collections.emptySet();
		}

		return StreamSupport.stream(mixins.spliterator(), false)
				.map(e -> {
					if (e instanceof JsonPrimitive str) {
						return str.getAsString();
					} else if (e instanceof JsonObject obj) {
						return obj.get("config").getAsString();
					} else {
						throw new RuntimeException("Incorrect fabric.mod.json format");
					}
				}).collect(Collectors.toSet());
	}
}
