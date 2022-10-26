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

package net.fabricmc.loom.util.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.gradle.api.provider.Property;

// Massive hack to work around WorkerExecutor.noIsolation() doing isolation checks.
public final class UnsafeWorkQueueHelper {
	private static final Map<String, SharedService> SERVICE_MAP = new ConcurrentHashMap<>();

	private UnsafeWorkQueueHelper() {
	}

	public static String create(SharedService service) {
		final String uuid = UUID.randomUUID().toString();
		SERVICE_MAP.put(uuid, service);

		return uuid;
	}

	public static <S> S get(Property<String> property, Class<S> clazz) {
		SharedService service = SERVICE_MAP.remove(property.get());

		if (service == null) {
			throw new NullPointerException("Failed to get service for " + clazz);
		}

		//noinspection unchecked
		return (S) service;
	}
}
