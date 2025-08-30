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

import java.util.Objects;

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
		add(fmj, "name", spec.getName());
		add(fmj, "description", spec.getDescription());
		addStringOrArray(fmj, "license", spec.getLicenses());
		addArray(fmj, "authors", spec.getAuthors(), this::generatePerson);

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
}
