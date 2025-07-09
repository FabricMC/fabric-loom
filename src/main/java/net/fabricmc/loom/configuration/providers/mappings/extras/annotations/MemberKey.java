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

import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

public record MemberKey(String owner, String name, String desc) {
	static class Serializer extends TypeAdapter<MemberKey> {
		@Override
		public void write(JsonWriter out, MemberKey value) throws IOException {
			if (value.desc.startsWith("(")) {
				out.value(value.owner + "." + value.name + value.desc);
			} else {
				out.value(value.owner + "." + value.name + ":" + value.desc);
			}
		}

		@Override
		public MemberKey read(JsonReader in) throws IOException {
			if (in.peek() == JsonToken.NULL) {
				in.nextNull();
				return null;
			}

			String str = in.nextString();
			int dotIndex = str.indexOf('.');

			if (dotIndex == -1) {
				throw new JsonSyntaxException("No dot in member key");
			}

			String owner = str.substring(0, dotIndex);
			String name;
			String desc;

			int colonIndex = str.indexOf(':', dotIndex + 1);

			if (colonIndex == -1) {
				int parenIndex = str.indexOf('(', dotIndex + 1);

				if (parenIndex == -1) {
					throw new JsonSyntaxException("No descriptor in member key");
				}

				name = str.substring(dotIndex + 1, parenIndex);
				desc = str.substring(parenIndex);
			} else {
				name = str.substring(dotIndex + 1, colonIndex);
				desc = str.substring(colonIndex + 1);
			}

			return new MemberKey(owner, name, desc);
		}
	}
}
