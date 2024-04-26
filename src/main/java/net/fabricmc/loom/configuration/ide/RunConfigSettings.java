/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021-2023 FabricMC
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

package net.fabricmc.loom.configuration.ide;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import javax.inject.Inject;

import org.gradle.api.Named;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.SourceSet;
import org.jetbrains.annotations.ApiStatus;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftSourceSets;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.Platform;
import net.fabricmc.loom.util.gradle.SourceSetHelper;

public class RunConfigSettings implements Named {
	/**
	 * Arguments for the JVM, such as system properties.
	 */
	private final List<String> vmArgs = new ArrayList<>();

	/**
	 * Arguments for the program's main class.
	 */
	private final List<String> programArgs = new ArrayList<>();

	/**
	 * The environment (or side) to run, usually client or server.
	 */
	private String environment;

	/**
	 * The full name of the run configuration, i.e. 'Minecraft Client'.
	 *
	 * <p>By default this is determined from the base name.
	 *
	 * <p>Note: unless the project is the root project (or {@link #appendProjectPathToConfigName} is disabled),
	 * the project path will be appended automatically, e.g. 'Minecraft Client (:some:project)'.
	 */
	private String configName;

	/**
	 * Whether to append the project path to the {@link #configName} when {@code project} isn't the root project.
	 *
	 * <p>Warning: could produce ambiguous run config names if disabled, unless used carefully in conjunction with
	 * {@link #configName}.
	 */
	private final Property<Boolean> appendProjectPathToConfigName;

	/**
	 * The default main class of the run configuration.
	 *
	 * <p>This can be overwritten in {@code fabric_installer.[method].json}. Note that this <em>doesn't</em> take
	 * priority over the main class specified in the Fabric installer configuration.
	 */
	private String defaultMainClass;

	/**
	 * The main class of the run configuration.
	 *
	 * <p>If unset, {@link #defaultMainClass} is used as the fallback, including the overwritten main class
	 * from installer files.
	 */
	private final Property<String> mainClass;

	/**
	 * The true entrypoint, this is usually dev launch injector.
	 * This should not be changed unless you know what you are doing.
	 */
	@ApiStatus.Internal
	@ApiStatus.Experimental
	private final Property<String> devLaunchMainClass;

	/**
	 * The source set getter, which obtains the source set from the given project.
	 */
	private Function<Project, SourceSet> source;

	/**
	 * The run directory for this configuration, relative to the root project directory.
	 */
	private String runDir;

	/**
	 * The base name of the run configuration, which is the name it is created with, i.e. 'client'
	 */
	private final String name;

	/**
	 * When true a run configuration file will be generated for IDE's.
	 *
	 * <p>By default only run configs on the root project will be generated.
	 */
	private boolean ideConfigGenerated;

	private final Map<String, Object> environmentVariables = new HashMap<>();

	private final Project project;
	private final LoomGradleExtension extension;

	@Inject
	public RunConfigSettings(Project project, String name) {
		this.name = name;
		this.project = project;
		this.appendProjectPathToConfigName = project.getObjects().property(Boolean.class).convention(true);
		this.extension = LoomGradleExtension.get(project);
		this.ideConfigGenerated = extension.isRootProject();
		this.mainClass = project.getObjects().property(String.class).convention(project.provider(() -> {
			Objects.requireNonNull(environment, "Run config " + name + " must specify environment");
			Objects.requireNonNull(defaultMainClass, "Run config " + name + " must specify default main class");
			return RunConfig.getMainClass(environment, extension, defaultMainClass);
		}));
		this.devLaunchMainClass = project.getObjects().property(String.class).convention("net.fabricmc.devlaunchinjector.Main");

		setSource(p -> {
			final String sourceSetName = MinecraftSourceSets.get(p).getSourceSetForEnv(getEnvironment());
			return SourceSetHelper.getSourceSetByName(sourceSetName, p);
		});

		runDir("run");
	}

	public Project getProject() {
		return project;
	}

	public LoomGradleExtension getExtension() {
		return extension;
	}

	@Override
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.configName = name;
	}

	public List<String> getVmArgs() {
		return vmArgs;
	}

	public List<String> getProgramArgs() {
		return programArgs;
	}

	public String getEnvironment() {
		return environment;
	}

	public void setEnvironment(String environment) {
		this.environment = environment;
	}

	public String getConfigName() {
		return configName;
	}

	public void setConfigName(String name) {
		this.configName = name;
	}

	public Property<Boolean> getAppendProjectPathToConfigName() {
		return appendProjectPathToConfigName;
	}

	public String getDefaultMainClass() {
		return defaultMainClass;
	}

	public void setDefaultMainClass(String defaultMainClass) {
		this.defaultMainClass = defaultMainClass;
	}

	/**
	 * The main class of the run configuration.
	 *
	 * <p>If unset, {@link #getDefaultMainClass defaultMainClass} is used as the fallback,
	 * including the overwritten main class from installer files.
	 */
	public Property<String> getMainClass() {
		return mainClass;
	}

	public String getRunDir() {
		return runDir;
	}

	public void setRunDir(String runDir) {
		this.runDir = runDir;
	}

	public SourceSet getSource(Project proj) {
		return source.apply(proj);
	}

	public void setSource(SourceSet source) {
		this.source = proj -> source;
	}

	public void setSource(Function<Project, SourceSet> sourceFn) {
		this.source = sourceFn;
	}

	public void environment(String environment) {
		setEnvironment(environment);
	}

	public void name(String name) {
		setConfigName(name);
	}

	public void defaultMainClass(String cls) {
		setDefaultMainClass(cls);
	}

	public void runDir(String dir) {
		setRunDir(dir);
	}

	public void vmArg(String arg) {
		vmArgs.add(arg);
	}

	public void vmArgs(String... args) {
		vmArgs.addAll(Arrays.asList(args));
	}

	public void vmArgs(Collection<String> args) {
		vmArgs.addAll(args);
	}

	public void property(String name, String value) {
		vmArg("-D" + name + "=" + value);
	}

	public void property(String name) {
		vmArg("-D" + name);
	}

	public void properties(Map<String, String> props) {
		props.forEach(this::property);
	}

	public void programArg(String arg) {
		programArgs.add(arg);
	}

	public void programArgs(String... args) {
		programArgs.addAll(Arrays.asList(args));
	}

	public void programArgs(Collection<String> args) {
		programArgs.addAll(args);
	}

	public void source(SourceSet source) {
		setSource(source);
	}

	public void source(String source) {
		setSource(proj -> SourceSetHelper.getSourceSetByName(source, proj));
	}

	public void ideConfigGenerated(boolean ideConfigGenerated) {
		this.ideConfigGenerated = ideConfigGenerated;
	}

	public Map<String, Object> getEnvironmentVariables() {
		return environmentVariables;
	}

	public void environmentVariable(String name, Object value) {
		environmentVariables.put(name, value);
	}

	/**
	 * Add the {@code -XstartOnFirstThread} JVM argument when on OSX.
	 */
	public void startFirstThread() {
		if (Platform.CURRENT.getOperatingSystem().isMacOS()) {
			vmArg("-XstartOnFirstThread");
		}
	}

	/**
	 * Removes the {@code nogui} argument for the server configuration. By default {@code nogui} is specified, this is
	 * a convenient way to remove it if wanted.
	 */
	public void serverWithGui() {
		programArgs.removeIf("nogui"::equals);
	}

	/**
	 * Configure run config with the default client options.
	 */
	public void client() {
		environment("client");
		defaultMainClass(Constants.Knot.KNOT_CLIENT);

		if (Platform.CURRENT.isRaspberryPi()) {
			getProject().getLogger().info("Raspberry Pi detected, setting MESA_GL_VERSION_OVERRIDE=4.3");
			environmentVariable("MESA_GL_VERSION_OVERRIDE", "4.3");
		}
	}

	/**
	 * Configure run config with the default server options.
	 */
	public void server() {
		programArg("nogui");
		environment("server");
		defaultMainClass(Constants.Knot.KNOT_SERVER);
	}

	/**
	 * Copies settings from another run configuration.
	 */
	public void inherit(RunConfigSettings parent) {
		vmArgs.addAll(0, parent.vmArgs);
		programArgs.addAll(0, parent.programArgs);
		environmentVariables.putAll(parent.environmentVariables);

		environment = parent.environment;
		configName = parent.configName;
		defaultMainClass = parent.defaultMainClass;
		source = parent.source;
		ideConfigGenerated = parent.ideConfigGenerated;
	}

	public void makeRunDir() {
		File file = new File(getProject().getProjectDir(), runDir);

		if (!file.exists()) {
			file.mkdir();
		}
	}

	public boolean isIdeConfigGenerated() {
		return ideConfigGenerated;
	}

	public void setIdeConfigGenerated(boolean ideConfigGenerated) {
		this.ideConfigGenerated = ideConfigGenerated;
	}

	@ApiStatus.Internal
	@ApiStatus.Experimental
	public Property<String> devLaunchMainClass() {
		return devLaunchMainClass;
	}
}
