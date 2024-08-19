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

package net.fabricmc.loom.util.service;

import org.gradle.api.provider.Provider;
import org.jetbrains.annotations.Nullable;

/**
 * A factory for creating {@link Service} instances.
 */
public interface ServiceFactory {
	/**
	 * Gets or creates a service instance with the given options.
	 *
	 * @param options The options to use.
	 * @param <O> The options type.
	 * @param <S> The service type.
	 * @return The service instance.
	 */
	default <O extends Service.Options, S extends Service<O>> S get(Provider<O> options) {
		return get(options.get());
	}

	/**
	 * Gets or creates a service instance with the given options, or returns null if the options are not present.
	 * @param options The options to use.
	 * @param <O> The options type.
	 * @param <S> The service type.
	 * @return The service instance, or null if the options are not present.
	 */
	@Nullable
	default <O extends Service.Options, S extends Service<O>> S getOrNull(Provider<O> options) {
		if (options.isPresent()) {
			return get(options);
		} else {
			return null;
		}
	}

	/**
	 * Gets or creates a service instance with the given options.
	 *
	 * @param options The options to use.
	 * @param <O> The options type.
	 * @param <S> The service type.
	 * @return The service instance.
	 */
	<O extends Service.Options, S extends Service<O>> S get(O options);
}
