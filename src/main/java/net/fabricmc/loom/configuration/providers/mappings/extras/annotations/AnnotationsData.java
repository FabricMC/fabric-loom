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

package net.fabricmc.loom.configuration.providers.mappings.extras.annotations;

import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.TypeAnnotationNode;

public record AnnotationsData(Map<String, ClassAnnotationData> classes) {
	public static final Gson GSON = new GsonBuilder()
			.disableHtmlEscaping()
			.setFieldNamingStrategy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
			.enableComplexMapKeySerialization()
			.registerTypeAdapter(TypeAnnotationNode.class, new TypeAnnotationNodeSerializer())
			.registerTypeAdapter(AnnotationNode.class, new AnnotationNodeSerializer())
			.registerTypeAdapterFactory(new SkipEmptyTypeAdapterFactory())
			.create();

	public static AnnotationsData read(Reader reader) {
		JsonObject json = GSON.fromJson(reader, JsonObject.class);

		if (!json.has("version")) {
			throw new JsonSyntaxException("Missing annotations version");
		}

		int version = json.getAsJsonPrimitive("version").getAsInt();

		if (version != 1) {
			throw new JsonSyntaxException("Invalid annotations version " + version + ". Try updating loom");
		}

		return GSON.fromJson(json, AnnotationsData.class);
	}

	public JsonObject toJson() {
		JsonObject json = GSON.toJsonTree(this).getAsJsonObject();
		JsonObject result = new JsonObject();
		result.addProperty("version", 1);
		result.asMap().putAll(json.asMap());
		return result;
	}

	public AnnotationsData merge(AnnotationsData other) {
		Map<String, ClassAnnotationData> newClassData = new LinkedHashMap<>(classes);
		other.classes.forEach((key, value) -> newClassData.merge(key, value, ClassAnnotationData::merge));
		return new AnnotationsData(newClassData);
	}
}
