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
import java.util.Collection;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

// https://github.com/google/gson/issues/512#issuecomment-1203356412
class SkipEmptyTypeAdapterFactory implements TypeAdapterFactory {
	@Override
	public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
		Class<?> rawType = type.getRawType();
		boolean isMap = Map.class.isAssignableFrom(rawType);

		if (!isMap && !Collection.class.isAssignableFrom(rawType)) {
			return null;
		}

		TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);

		return new TypeAdapter<>() {
			@Override
			public void write(JsonWriter out, T value) throws IOException {
				if (value == null || isEmpty(value)) {
					delegate.write(out, null);
				} else {
					delegate.write(out, value);
				}
			}

			@Override
			public T read(JsonReader in) throws IOException {
				return delegate.read(in);
			}

			private boolean isEmpty(T value) {
				return isMap ? ((Map<?, ?>) value).isEmpty() : ((Collection<?>) value).isEmpty();
			}
		};
	}
}
