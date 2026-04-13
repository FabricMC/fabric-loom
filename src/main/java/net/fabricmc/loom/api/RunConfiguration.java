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

package net.fabricmc.loom.api;

import org.gradle.api.Named;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.SourceSet;
import org.jetbrains.annotations.ApiStatus;

/**
 * Represents a run configuration for Minecraft, these can presented via an IDE run configuration or a Gradle task.
 *
 * <p>This is used to configure how Minecraft is run, including JVM arguments, program arguments, environment variables,
 * and the main class to run.
 */
public interface RunConfiguration extends Named {
	/**
	 * The full name of the run configuration, i.e. 'Minecraft Client'.
	 *
	 * <p>By default this is determined from the base name.
	 *
	 * <p>Note: unless the project is the root project (or {@link #getAppendProjectPathToDisplayName()} is disabled),
	 * the project path will be appended automatically, e.g. 'Minecraft Client (:some:project)'.
	 */
	Property<String> getDisplayName();

	/**
	 * Arguments for the JVM, such as system properties.
	 */
	ListProperty<String> getJvmArguments();

	/**
	 * Arguments for the program, usually Minecraft specific arguments.
	 */
	ListProperty<String> getProgramArguments();

	/**
	 * Environment variables to set when running the configuration.
	 */
	MapProperty<String, Object> getEnvironmentVars();

	/**
	 * System properties to set when running the configuration.
	 */
	MapProperty<String, String> getSystemProperties();

	/**
	 * The environment (or side) to run, usually client or server.
	 */
	Property<String> getRuntimeEnvironment();

	/**
	 * Whether to append the project path to the {@link #getDisplayName()} when {@code project} isn't the root project.
	 *
	 * <p>Warning: could produce ambiguous run config names if disabled, unless used carefully in conjunction with
	 * {@link #getDisplayName()}.
	 */
	Property<Boolean> getAppendProjectPathToDisplayName();

	/**
	 * The main class of the run configuration.
	 */
	Property<String> getMainClass();

	/**
	 * The name of the {@link SourceSet} to use for the run configuration.
	 */
	Property<String> getSourceSet();

	/**
	 * The run directory for this configuration, relative to the root project directory.
	 */
	DirectoryProperty getRunDirectory();

	/**
	 * When true a run configuration file will be generated for IDE's.
	 *
	 * <p>By default only run configs on the root project will be generated.
	 */
	Property<Boolean> getGenerateRunConfig();

	/**
	 * Group this run config under the given folder.
	 *
	 * <p>This is currently only supported on IntelliJ IDEA.
	 *
	 * @return The property used to set the config folder.
	 */
	Property<String> getIdeConfigFolder();

	/**
	 * The true entrypoint, this is usually dev launch injector.
	 * This should not be changed unless you know what you are doing.
	 */
	@ApiStatus.Experimental
	Property<String> getDevLaunchMainClass();

	default void inherit(RunConfiguration parent) {
		getDisplayName().convention(parent.getDisplayName());
		getJvmArguments().convention(parent.getJvmArguments());
		getProgramArguments().convention(parent.getProgramArguments());
		getEnvironmentVars().convention(parent.getEnvironmentVars());
		getRuntimeEnvironment().convention(parent.getRuntimeEnvironment());
		getAppendProjectPathToDisplayName().convention(parent.getAppendProjectPathToDisplayName());
		getMainClass().convention(parent.getMainClass());
		getSourceSet().convention(parent.getSourceSet());
		getRunDirectory().convention(parent.getRunDirectory());
		getGenerateRunConfig().convention(parent.getGenerateRunConfig());
		getIdeConfigFolder().convention(parent.getIdeConfigFolder());
		getDevLaunchMainClass().convention(parent.getDevLaunchMainClass());
	}

	/**
	 * Configure run config with the default client options.
	 */
	default void client() {
		getRuntimeEnvironment().set("client");
	}

	/**
	 * Configure run config with the default server options.
	 */
	default void server() {
		getRuntimeEnvironment().set("server");
		getProgramArguments().add("nogui");
	}
}
