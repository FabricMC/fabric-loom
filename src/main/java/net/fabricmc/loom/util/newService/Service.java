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

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.jetbrains.annotations.ApiStatus;

/**
 * A service is used to manage a set of data or a task that may be reused multiple times.
 *
 * @param <O> The options type.
 */
public abstract class Service<O extends Service.Options> {
	/**
	 * Gets the options for this service.
	 *
	 * @return The options.
	 */
	protected abstract O options();

	/**
	 * Return the factory that created this service, this can be used to get nested services.
	 *
	 * @return The {@link ServiceFactory} instance.
	 */
	protected abstract ServiceFactory serviceFactory();

	/**
	 * Create a instance of the options class for the given service class.
	 * @param project The {@link Project} to create the options for.
	 * @param serviceClass The {@link Class} of the service that the options are for.
	 * @param optionsClass The {@link Class} of the options to create.
	 * @param action An action to configure the options.
	 * @return The created options instance.
	 */
	public static <O extends Service.Options, S extends Service<O>> Provider<O> createOptions(Project project, Class<S> serviceClass, Class<O> optionsClass, Action<O> action) {
		return project.provider(() -> {
			O options = project.getObjects().newInstance(optionsClass);
			options.getServiceClass().set(serviceClass.getName());
			options.getServiceClass().finalizeValue();
			action.execute(options);
			return options;
		});
	}

	/**
	 * The base type of options class for a service.
	 */
	public interface Options {
		@Input
		@ApiStatus.Internal
		Property<String> getServiceClass();
	}
}
