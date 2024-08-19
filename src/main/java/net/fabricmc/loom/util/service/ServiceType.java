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

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;

/**
 * A record to hold the options and service class for a service.
 * @param optionsClass The options class for the service.
 * @param serviceClass The service class.
 */
public record ServiceType<O extends Service.Options, S extends Service<O>>(Class<O> optionsClass, Class<S> serviceClass) {
	/**
	 * Create an instance of the options class for the given service class.
	 * @param project The {@link Project} to create the options for.
	 * @param action An action to configure the options.
	 * @return The created options instance.
	 */
	public Provider<O> create(Project project, Action<O> action) {
		return project.provider(() -> {
			O options = project.getObjects().newInstance(optionsClass);
			options.getServiceClass().set(serviceClass.getName());
			options.getServiceClass().finalizeValue();
			action.execute(options);
			return options;
		});
	}
}
