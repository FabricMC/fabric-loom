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

package net.fabricmc.loom.api.fabricapi;

import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Optional;
import org.jetbrains.annotations.ApiStatus;

/**
 * Represents the settings for game and/or client tests.
 */
@ApiStatus.Experimental
public interface GameTestSettings {
	/**
	 * Contains a boolean property indicating whether a new source set should be created for the tests.
	 *
	 * <p>Default: false
	 */
	Property<Boolean> getCreateSourceSet();

	/**
	 * Contains a string property representing the mod ID associated with the tests.
	 *
	 * <p>This must be set when {@link #getCreateSourceSet()} is set.
	 */
	@Optional
	Property<String> getModId();

	/**
	 * Contains a boolean property indicating whether a run configuration will be created for the server side game tests, using Vanilla Game Test framework.
	 *
	 * <p>Default: true
	 */
	Property<Boolean> getEnableGameTests();

	/**
	 * Contains a boolean property indicating whether a run configuration will be created for the client side game tests, using the Fabric API Client Test framework.
	 *
	 * <p>Default: true
	 */
	Property<Boolean> getEnableClientGameTests();

	/**
	 * Contains a boolean property indicating whether the eula has been accepted. By enabling this you agree to the Minecraft EULA located at <a href="https://aka.ms/MinecraftEULA">https://aka.ms/MinecraftEULA</a>.
	 *
	 * <p>This only works when {@link #getEnableClientGameTests()} is enabled.
	 *
	 * <p>Default: false
	 */
	Property<Boolean> getEula();

	/**
	 * Contains a boolean property indicating whether the run directories should be cleared before running the tests.
	 *
	 * <p>This only works when {@link #getEnableClientGameTests()} is enabled.
	 *
	 * <p>Default: true
	 */
	Property<Boolean> getClearRunDirectory();

	/**
	 * Contains a string property representing the username to use for the client side game tests.
	 *
	 * <p>This only works when {@link #getEnableClientGameTests()} is enabled.
	 *
	 * <p>Default: Player0
	 */
	@Optional
	Property<String> getUsername();
}
