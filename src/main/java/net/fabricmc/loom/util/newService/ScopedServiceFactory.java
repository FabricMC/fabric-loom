/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2024 FabricMC
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

package net.fabricmc.loom.util.newService;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import net.fabricmc.loom.util.gradle.GradleTypeAdapter;

/**
 * An implementation of {@link ServiceFactory} that creates services scoped to the factory instance.
 * When the factory is closed, all services created by it are closed and discarded.
 */
public final class ScopedServiceFactory implements ServiceFactory, Closeable {
	private final Map<Service.Options, Service<?>> servicesIdentityMap = new IdentityHashMap<>();
	private final Map<String, Service<?>> servicesJsonMap = new HashMap<>();

	@Override
	public <O extends Service.Options, S extends Service<O>> S get(O options) {
		// First check if the service is already created, using the identity map saving the need to serialize the options
		//noinspection unchecked
		S service = (S) servicesIdentityMap.get(options);

		if (service != null) {
			return service;
		}

		// TODO skip serialization if we know there is no service with the same type

		// If the service is not already created, serialize the options and check the json map as it may be an equivalent service
		String key = getOptionsCacheKey(options);
		//noinspection unchecked
		service = (S) servicesJsonMap.get(key);

		if (service != null) {
			return service;
		}

		// We need to create the service from the provided options
		final Class<? extends S> serviceClass;

		// Find the service class
		try {
			//noinspection unchecked
			serviceClass = (Class<? extends S>) Class.forName(options.getServiceClass().get());
		} catch (ClassNotFoundException e) {
			throw new RuntimeException("Failed to find service class: " + options.getServiceClass().get(), e);
		}

		// Generate the implementation class and instantiate it
		try {
			// Check there is only 1 constructor
			if (serviceClass.getDeclaredConstructors().length != 1) {
				throw new RuntimeException("Service class must have exactly 1 constructor");
			}

			//noinspection unchecked
			service = (S) serviceClass.getDeclaredConstructors()[0].newInstance(options, this);
		} catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
			throw new RuntimeException("Failed to create service instance", e);
		}

		servicesIdentityMap.put(options, service);
		servicesJsonMap.put(key, service);

		return service;
	}

	private String getOptionsCacheKey(Service.Options options) {
		return GradleTypeAdapter.GSON.toJson(options);
	}

	@Override
	public void close() throws IOException {
		for (Service<?> service : servicesIdentityMap.values()) {
			if (service instanceof Closeable closeable) {
				closeable.close();
			}
		}

		servicesIdentityMap.clear();
		servicesJsonMap.clear();
	}
}
