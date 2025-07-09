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

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.tree.AnnotationNode;

class AnnotationNodeSerializer implements JsonSerializer<AnnotationNode>, JsonDeserializer<AnnotationNode> {
	@Override
	public AnnotationNode deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
		JsonObject jsonObject = json.getAsJsonObject();
		String desc = jsonObject.getAsJsonPrimitive("desc").getAsString();
		AnnotationNode annotation = new AnnotationNode(desc);
		JsonObject values = jsonObject.getAsJsonObject("values");

		if (values != null) {
			for (Map.Entry<String, JsonElement> entry : values.entrySet()) {
				deserializeAnnotationValue(annotation, entry.getKey(), entry.getValue(), context);
			}
		}

		return annotation;
	}

	private static void deserializeAnnotationValue(AnnotationVisitor visitor, @Nullable String name, JsonElement value, JsonDeserializationContext context) throws JsonParseException {
		JsonObject obj = value.getAsJsonObject();
		switch (obj.getAsJsonPrimitive("type").getAsString()) {
		case "byte" -> visitor.visit(name, obj.getAsJsonPrimitive("value").getAsByte());
		case "boolean" -> visitor.visit(name, obj.getAsJsonPrimitive("value").getAsBoolean());
		case "char" -> visitor.visit(name, obj.getAsJsonPrimitive("value").getAsString().charAt(0));
		case "short" -> visitor.visit(name, obj.getAsJsonPrimitive("value").getAsShort());
		case "int" -> visitor.visit(name, obj.getAsJsonPrimitive("value").getAsInt());
		case "long" -> visitor.visit(name, obj.getAsJsonPrimitive("value").getAsLong());
		case "float" -> visitor.visit(name, obj.getAsJsonPrimitive("value").getAsFloat());
		case "double" -> visitor.visit(name, obj.getAsJsonPrimitive("value").getAsDouble());
		case "string" -> visitor.visit(name, obj.getAsJsonPrimitive("value").getAsString());
		case "class" ->
				visitor.visit(name, org.objectweb.asm.Type.getType(obj.getAsJsonPrimitive("value").getAsString()));
		case "enum_constant" ->
				visitor.visitEnum(name, obj.getAsJsonPrimitive("owner").getAsString(), obj.getAsJsonPrimitive("name").getAsString());
		case "annotation" -> {
			AnnotationNode annotation = context.deserialize(obj, AnnotationNode.class);
			AnnotationVisitor av = visitor.visitAnnotation(name, annotation.desc);

			if (av != null) {
				annotation.accept(av);
			}
		}
		case "array" -> {
			AnnotationVisitor av = visitor.visitArray(name);

			if (av != null) {
				for (JsonElement element : obj.getAsJsonArray("value")) {
					deserializeAnnotationValue(av, null, element, context);
				}

				av.visitEnd();
			}
		}
		}
	}

	@Override
	public JsonElement serialize(AnnotationNode src, Type typeOfSrc, JsonSerializationContext context) {
		JsonObject json = new JsonObject();
		json.addProperty("desc", src.desc);

		if (src.values != null && !src.values.isEmpty()) {
			JsonObject values = new JsonObject();

			for (int i = 0; i < src.values.size() - 1; i += 2) {
				String name = String.valueOf(src.values.get(i));
				Object value = src.values.get(i + 1);
				values.add(name, serializeAnnotationValue(value, context));
			}

			json.add("values", values);
		}

		return json;
	}

	private static JsonObject serializeAnnotationValue(Object value, JsonSerializationContext context) {
		JsonObject json = new JsonObject();

		switch (value) {
		case Byte b -> {
			json.addProperty("type", "byte");
			json.addProperty("value", b);
		}
		case Boolean b -> {
			json.addProperty("type", "boolean");
			json.addProperty("value", b);
		}
		case Character c -> {
			json.addProperty("type", "char");
			json.addProperty("value", c);
		}
		case Short s -> {
			json.addProperty("type", "short");
			json.addProperty("value", s);
		}
		case Integer i -> {
			json.addProperty("type", "int");
			json.addProperty("value", i);
		}
		case Long l -> {
			json.addProperty("type", "long");
			json.addProperty("value", l);
		}
		case Float f -> {
			json.addProperty("type", "float");
			json.addProperty("value", f);
		}
		case Double d -> {
			json.addProperty("type", "double");
			json.addProperty("value", d);
		}
		case String str -> {
			json.addProperty("type", "string");
			json.addProperty("value", str);
		}
		case org.objectweb.asm.Type type -> {
			json.addProperty("type", "class");
			json.addProperty("value", type.getDescriptor());
		}
		case String[] enumConstant -> {
			json.addProperty("type", "enum_constant");
			json.addProperty("owner", enumConstant[0]);
			json.addProperty("name", enumConstant[1]);
		}
		case AnnotationNode annotation -> {
			json.addProperty("type", "annotation");
			JsonObject annJson = context.serialize(annotation).getAsJsonObject();
			json.asMap().putAll(annJson.asMap());
		}
		case List<?> list -> {
			json.addProperty("type", "array");
			JsonArray array = new JsonArray(list.size());

			for (Object o : list) {
				array.add(serializeAnnotationValue(o, context));
			}

			json.add("value", array);
		}
		default -> throw new IllegalArgumentException("Unknown annotation value type: " + value);
		}

		return json;
	}
}
