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

package net.fabricmc.loom.util.fmj.gen;

import static net.fabricmc.loom.util.fmj.gen.GeneratorUtils.add;
import static net.fabricmc.loom.util.fmj.gen.GeneratorUtils.addArray;
import static net.fabricmc.loom.util.fmj.gen.GeneratorUtils.addRequired;
import static net.fabricmc.loom.util.fmj.gen.GeneratorUtils.addStringOrArray;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.api.fmj.FabricModJsonV1Spec;

// Opposite of https://github.com/FabricMC/fabric-loader/blob/master/src/main/java/net/fabricmc/loader/impl/metadata/V1ModMetadataParser.java
public final class FabricModJsonV1Generator implements FabricModJsonGenerator<FabricModJsonV1Spec> {
	private static final int VERSION = 1;

	public static final FabricModJsonV1Generator INSTANCE = new FabricModJsonV1Generator();

	private FabricModJsonV1Generator() {
	}

	public String generate(FabricModJsonV1Spec spec) {
		Objects.requireNonNull(spec);

		JsonObject fmj = new JsonObject();
		fmj.addProperty("schemaVersion", VERSION);

		// Required
		addRequired(fmj, "id", spec.getModId());
		addRequired(fmj, "version", spec.getVersion());

		// All other fields are optional
		// Match the order as specified in V1ModMetadataParser to make it easier to compare

		addArray(fmj, "provides", spec.getProvides(), JsonPrimitive::new);
		add(fmj, "environment", spec.getEnvironment());
		add(fmj, "entrypoints", spec.getEntrypoints(), this::generateEntrypoints);
		addArray(fmj, "jars", spec.getNestedJars(), this::generateJar);
		// mixins
		// accessWidener
		// depends
		// recommends
		// suggests
		// conflicts
		// breaks
		// requires
		add(fmj, "name", spec.getName());
		add(fmj, "description", spec.getDescription());
		addArray(fmj, "authors", spec.getAuthors(), this::generatePerson);
		addArray(fmj, "contributors", spec.getContributors(), this::generatePerson);
		add(fmj, "contact", spec.getContactInformation());
		addStringOrArray(fmj, "license", spec.getLicenses());
		// icon
		// languageAdapters
		// custom

		return LoomGradlePlugin.GSON.toJson(fmj);
	}

	private JsonElement generatePerson(FabricModJsonV1Spec.Person person) {
		if (person.getContactInformation().get().isEmpty()) {
			return new JsonPrimitive(person.getName().get());
		}

		JsonObject json = new JsonObject();
		addRequired(json, "name", person.getName());
		add(json, "contact", person.getContactInformation());

		return json;
	}

	private JsonObject generateEntrypoints(List<FabricModJsonV1Spec.Entrypoint> entrypoints) {
		Map<String, List<FabricModJsonV1Spec.Entrypoint>> entrypointsMap = entrypoints.stream()
				.collect(Collectors.groupingBy(entrypoint -> entrypoint.getEntrypoint().get()));

		JsonObject json = new JsonObject();
		entrypointsMap.forEach((entrypoint, entries) -> json.add(entrypoint, generateEntpypoint(entries)));
		return json;
	}

	private JsonArray generateEntpypoint(List<FabricModJsonV1Spec.Entrypoint> entries) {
		JsonArray json = new JsonArray();

		for (FabricModJsonV1Spec.Entrypoint entry : entries) {
			json.add(generateEntrypointEntry(entry));
		}

		return json;
	}

	private JsonElement generateEntrypointEntry(FabricModJsonV1Spec.Entrypoint entrypoint) {
		if (!entrypoint.getAdapter().isPresent()) {
			return new JsonPrimitive(entrypoint.getValue().get());
		}

		JsonObject json = new JsonObject();
		addRequired(json, "value", entrypoint.getValue());
		addRequired(json, "adapter", entrypoint.getAdapter());
		return json;
	}

	private JsonObject generateJar(String jar) {
		JsonObject json = new JsonObject();
		json.addProperty("file", jar);
		return json;
	}
}
