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

import java.util.Map;

import org.gradle.api.Named;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.SourceSet;
import org.jetbrains.annotations.ApiStatus;

import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.Platform;

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
	 * <p>Note: unless the project is the root project (or {@link #getAppendProjectPathToConfigName()} is disabled),
	 * the project path will be appended automatically, e.g. 'Minecraft Client (:some:project)'.
	 */
	Property<String> getConfigurationName();

	/**
	 * Arguments for the JVM, such as system properties.
	 */
	ListProperty<String> getJVMArguments();

	/**
	 * Arguments for the program, usually Minecraft specific arguments.
	 */
	ListProperty<String> getProgramArguments();

	/**
	 * Environment variables to set when running the configuration.
	 */
	MapProperty<String, Object> getEnvironmentVars();

	/**
	 * The environment (or side) to run, usually client or server.
	 */
	Property<String> getRuntimeEnvironment();

	/**
	 * Whether to append the project path to the {@link #getConfigurationName()} when {@code project} isn't the root project.
	 *
	 * <p>Warning: could produce ambiguous run config names if disabled, unless used carefully in conjunction with
	 * {@link #getConfigurationName()}.
	 */
	Property<Boolean> getAppendProjectPathToConfigName();

	/**
	 * The main class of the run configuration.
	 */
	Property<String> getMainClass();

	/**
	 * The source set to use for the run configuration.
	 */
	Property<SourceSet> getSourceSet();

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
		getConfigurationName().convention(parent.getConfigurationName());
		getJVMArguments().convention(parent.getJVMArguments());
		getProgramArguments().convention(parent.getProgramArguments());
		getEnvironmentVars().convention(parent.getEnvironmentVars());
		getRuntimeEnvironment().convention(parent.getRuntimeEnvironment());
		getAppendProjectPathToConfigName().convention(getAppendProjectPathToConfigName());
		getMainClass().convention(parent.getMainClass());
		getSourceSet().convention(parent.getSourceSet());
		getRunDirectory().convention(parent.getRunDirectory());
		getGenerateRunConfig().convention(parent.getGenerateRunConfig());
		getIdeConfigFolder().convention(parent.getIdeConfigFolder());
	}

	/**
	 * Configure run config with the default client options.
	 */
	default void client() {
		getRuntimeEnvironment().convention("client");
		getMainClass().convention(Constants.Knot.KNOT_CLIENT);

		if (Platform.CURRENT.isRaspberryPi()) {
			getEnvironmentVars().put("MESA_GL_VERSION_OVERRIDE", "4.3");
		}
	}

	/**
	 * Configure run config with the default server options.
	 */
	default void server() {
		getProgramArguments().add("nogui");
		getRuntimeEnvironment().convention("server");
		getMainClass().convention(Constants.Knot.KNOT_SERVER);
	}

	default void property(String name, String value) {
		getJVMArguments().add("-D%s=%s".formatted(name, value));
	}

	default void property(String name) {
		getJVMArguments().add("-D%s".formatted(name));
	}

	default void properties(Map<String, String> props) {
		props.forEach(this::property);
	}
}
