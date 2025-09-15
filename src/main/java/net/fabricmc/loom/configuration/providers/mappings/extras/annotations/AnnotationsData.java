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

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import org.gradle.api.Project;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.commons.AnnotationRemapper;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.TypeAnnotationNode;

import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.providers.mappings.MappingConfiguration;
import net.fabricmc.loom.util.TinyRemapperHelper;
import net.fabricmc.loom.util.service.ServiceFactory;
import net.fabricmc.tinyremapper.TinyRemapper;

public record AnnotationsData(Map<String, ClassAnnotationData> classes, String namespace) {
	public static final Gson GSON = new GsonBuilder()
			.disableHtmlEscaping()
			.setFieldNamingStrategy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
			.enableComplexMapKeySerialization()
			.registerTypeAdapter(TypeAnnotationNode.class, new TypeAnnotationNodeSerializer())
			.registerTypeAdapter(AnnotationNode.class, new AnnotationNodeSerializer())
			.registerTypeAdapterFactory(new SkipEmptyTypeAdapterFactory())
			.create();
	private static final Type LIST_TYPE = new TypeToken<List<AnnotationNode>>() { }.getType();
	private static final int CURRENT_VERSION = 1;

	public AnnotationsData {
		if (namespace == null) {
			namespace = MappingsNamespace.NAMED.toString();
		}
	}

	public static AnnotationsData read(Reader reader) {
		JsonObject json = GSON.fromJson(reader, JsonObject.class);
		checkVersion(json);
		return GSON.fromJson(json, AnnotationsData.class);
	}

	public static List<AnnotationsData> readList(Reader reader) {
		JsonObject json = GSON.fromJson(reader, JsonObject.class);
		checkVersion(json);
		JsonElement values = json.get("values");

		if (values == null || values.isJsonNull()) {
			return List.of(GSON.fromJson(json, AnnotationsData.class));
		}

		return GSON.fromJson(values, LIST_TYPE);
	}

	private static void checkVersion(JsonObject json) {
		if (!json.has("version")) {
			throw new JsonSyntaxException("Missing annotations version");
		}

		int version = json.getAsJsonPrimitive("version").getAsInt();

		if (version != CURRENT_VERSION) {
			throw new JsonSyntaxException("Invalid annotations version " + version + ". Try updating loom");
		}
	}

	public JsonObject toJson() {
		JsonObject json = GSON.toJsonTree(this).getAsJsonObject();
		JsonObject result = new JsonObject();
		result.addProperty("version", CURRENT_VERSION);
		result.asMap().putAll(json.asMap());
		return result;
	}

	public static JsonObject listToJson(List<AnnotationsData> annotationsData) {
		if (annotationsData.size() == 1) {
			return annotationsData.getFirst().toJson();
		}

		JsonObject result = new JsonObject();
		result.addProperty("version", CURRENT_VERSION);
		result.add("values", GSON.toJsonTree(annotationsData));
		return result;
	}

	public AnnotationsData merge(AnnotationsData other) {
		if (!namespace.equals(other.namespace)) {
			throw new IllegalArgumentException("Cannot merge annotations from namespace " + other.namespace + " into annotations from namespace " + this.namespace);
		}

		Map<String, ClassAnnotationData> newClassData = new LinkedHashMap<>(classes);
		other.classes.forEach((key, value) -> newClassData.merge(key, value, ClassAnnotationData::merge));
		return new AnnotationsData(newClassData, namespace);
	}

	public AnnotationsData remap(TinyRemapper remapper, String newNamespace) {
		return new AnnotationsData(
				remapMap(
						classes,
						entry -> remapper.getEnvironment().getRemapper().map(entry.getKey()),
						entry -> entry.getValue().remap(entry.getKey(), remapper)
				),
				newNamespace
		);
	}

	static AnnotationNode remap(AnnotationNode node, TinyRemapper remapper) {
		AnnotationNode remapped = new AnnotationNode(remapper.getEnvironment().getRemapper().mapDesc(node.desc));
		node.accept(new AnnotationRemapper(node.desc, remapped, remapper.getEnvironment().getRemapper()));
		return remapped;
	}

	static TypeAnnotationNode remap(TypeAnnotationNode node, TinyRemapper remapper) {
		TypeAnnotationNode remapped = new TypeAnnotationNode(node.typeRef, node.typePath, remapper.getEnvironment().getRemapper().mapDesc(node.desc));
		node.accept(new AnnotationRemapper(node.desc, remapped, remapper.getEnvironment().getRemapper()));
		return remapped;
	}

	static <K, V> Map<K, V> remapMap(Map<K, V> map, Function<Map.Entry<K, V>, K> keyRemapper, Function<Map.Entry<K, V>, V> valueRemapper) {
		Map<K, V> result = LinkedHashMap.newLinkedHashMap(map.size());

		for (Map.Entry<K, V> entry : map.entrySet()) {
			if (result.put(keyRemapper.apply(entry), valueRemapper.apply(entry)) != null) {
				throw new IllegalStateException("Remapping annotations resulted in duplicate key: " + keyRemapper.apply(entry));
			}
		}

		return result;
	}

	@Nullable
	public static AnnotationsData getRemappedAnnotations(MappingsNamespace targetNamespace, MappingConfiguration mappingConfiguration, Project project, ServiceFactory serviceFactory, String newNamespace) throws IOException {
		List<AnnotationsData> datas = mappingConfiguration.getAnnotationsData();

		if (datas.isEmpty()) {
			return null;
		}

		Map<String, TinyRemapper> existingRemappers = new HashMap<>();
		AnnotationsData result = datas.getFirst().remap(targetNamespace, project, serviceFactory, newNamespace, existingRemappers);

		for (int i = 1; i < datas.size(); i++) {
			result = result.merge(datas.get(i).remap(targetNamespace, project, serviceFactory, newNamespace, existingRemappers));
		}

		return result;
	}

	private AnnotationsData remap(MappingsNamespace targetNamespace, Project project, ServiceFactory serviceFactory, String newNamespace, Map<String, TinyRemapper> existingRemappers) throws IOException {
		if (namespace.equals(targetNamespace.toString())) {
			return this;
		}

		TinyRemapper remapper = existingRemappers.get(namespace);

		if (remapper == null) {
			remapper = TinyRemapperHelper.getTinyRemapper(project, serviceFactory, namespace, newNamespace);
			existingRemappers.put(namespace, remapper);
		}

		return remap(remapper, newNamespace);
	}
}
