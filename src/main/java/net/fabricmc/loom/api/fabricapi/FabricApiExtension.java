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

package net.fabricmc.loom.api.fabricapi;

import org.gradle.api.Action;
import org.gradle.api.artifacts.Dependency;

/**
 * A gradle extension with specific functionality related to Fabric API.
 */
public interface FabricApiExtension {
	/**
	 * Get a {@link Dependency} for a given Fabric API module.
	 *
	 * @param moduleName The name of the module.
	 * @param fabricApiVersion The main Fabric API version.
	 * @return A {@link Dependency} for the module.
	 */
	Dependency module(String moduleName, String fabricApiVersion);

	/**
	 * Get the version of a Fabric API module.
	 * @param moduleName The name of the module.
	 * @param fabricApiVersion The main Fabric API version.
	 * @return The version of the module.
	 */
	String moduleVersion(String moduleName, String fabricApiVersion);

	/**
	 * Configuration data generation using the default settings.
	 */
	void configureDataGeneration();

	/**
	 * Configuration data generation using the specified settings.
	 * @param action An action to configure specific data generation settings. See {@link DataGenerationSettings} for more information.
	 */
	void configureDataGeneration(Action<DataGenerationSettings> action);
}
