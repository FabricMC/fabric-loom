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
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import javax.inject.Inject;

import org.gradle.api.Named;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.SourceSet;

import net.fabricmc.loom.api.RunConfiguration;
import net.fabricmc.loom.util.Platform;
import net.fabricmc.loom.util.gradle.SourceSetHelper;

public abstract class RunConfigSettings implements Named, RunConfiguration, RunConfigurationInternal {
	// The base name of the run configuration, which is the name it is created with, i.e. 'client'
	private final String name;
	private final Project project;

	@Inject
	public RunConfigSettings(Project project, String name) {
		this.name = name;
		this.project = project;
		DefaultRunConfigurationSettings.configureDefaults(this, project);
	}

	@Override
	public String getName() {
		return name;
	}

	// Backwards compatibility shims:

	// Note: Overridden for backwards compatibility
	@Override
	public abstract Property<Boolean> getAppendProjectPathToDisplayName();

	// Note: Overridden for backwards compatibility
	@Override
	public abstract Property<String> getMainClass();

	// Note: Overridden for backwards compatibility
	@Override
	public void client() {
		RunConfigurationInternal.super.client();
	}

	// Note: Overridden for backwards compatibility
	@Override
	public void server() {
		RunConfigurationInternal.super.server();
	}

	// Note: Overload method for backwards compatibility
	public void inherit(RunConfigSettings parent) {
		RunConfigurationInternal.super.inherit(parent);
	}

	/**
	 * Removes the {@code nogui} argument for the server configuration. By default {@code nogui} is specified, this is
	 * a convenient way to remove it if wanted.
	 */
	public void serverWithGui() {
		getProgramArgs().removeIf("nogui"::equals);
	}

	// Note: Overridden for backwards compatibility
	@Override
	public abstract Property<String> getIdeConfigFolder();

	// Deprecated methods:

	/**
	 * @deprecated No replacement
	 */
	@Deprecated
	public Project getProject() {
		return project;
	}

	/**
	 * @deprecated Use {@link #getDisplayName()} instead.
	 */
	@Deprecated
	public void setName(String name) {
		this.getDisplayName().set(name);
	}

	/**
	 * @deprecated Use {@link #getJvmArguments()} instead.
	 */
	@Deprecated
	public List<String> getVmArgs() {
		return getJvmArguments().get();
	}

	/**
	 * @deprecated Use {@link #getProgramArgs()} instead.
	 */
	@Deprecated
	public List<String> getProgramArgs() {
		return getProgramArgs();
	}

	/**
	 * @deprecated Use {@link #getRuntimeEnvironment()} instead.
	 */
	@Deprecated
	public String getEnvironment() {
		return getRuntimeEnvironment().get();
	}

	/**
	 * @deprecated Use {@link #getRuntimeEnvironment()} instead.
	 */
	@Deprecated
	public void setEnvironment(String environment) {
		getRuntimeEnvironment().set(environment);
	}

	/**
	 * @deprecated Use {@link #getDisplayName()} instead.
	 */
	@Deprecated
	public String getConfigName() {
		return getDisplayName().get();
	}

	/**
	 * @deprecated Use {@link #getDisplayName()} instead.
	 */
	@Deprecated
	public void setConfigName(String name) {
		getDisplayName().set(name);
	}

	/**
	 * @deprecated Use {@link #getMainClass()} instead.
	 */
	@Deprecated
	public String getDefaultMainClass() {
		return getMainClass().get();
	}

	/**
	 * @deprecated Use {@link #getMainClass()} instead.
	 */
	@Deprecated
	public void setDefaultMainClass(String defaultMainClass) {
		getMainClass().convention(defaultMainClass);
	}

	/**
	 * @deprecated Use {@link #getRunDirectory()} instead.
	 */
	@Deprecated
	public String getRunDir() {
		File runDir = getRunDirectory().getAsFile().get();
		File projectDir = project.getProjectDir();
		String relative = projectDir.toURI().relativize(runDir.toURI()).getPath();

		if (relative.startsWith("..")) {
			throw new IllegalStateException("Run directory '%s' is not relative to the project directory '%s'".formatted(runDir, projectDir));
		}

		return relative;
	}

	/**
	 * @deprecated Use {@link #getRunDirectory()} instead.
	 */
	@Deprecated
	public void setRunDir(String runDir) {
		getRunDirectory().set(getProject().file(runDir));
	}

	/**
	 * @deprecated Use {@link #getSourceSet()} instead.
	 */
	@Deprecated
	public SourceSet getSource(Project proj) {
		return SourceSetHelper.getSourceSetByName(getSourceSet().get(), project);
	}

	/**
	 * @deprecated Use {@link #getSourceSet()} instead.
	 */
	@Deprecated
	public void setSource(SourceSet source) {
		getSourceSet().set(source.getName());
	}

	/**
	 * @deprecated Use {@link #getSourceSet()} instead.
	 */
	@Deprecated
	public void setSource(Function<Project, SourceSet> sourceFn) {
		getSourceSet().set(getProject().provider(() -> sourceFn.apply(getProject()).getName()));
	}

	/**
	 * @deprecated Use {@link #getEnvironment()} instead.
	 */
	@Deprecated
	public void environment(String environment) {
		setEnvironment(environment);
	}

	/**
	 * @deprecated Use {@link #getDisplayName()} instead.
	 */
	@Deprecated
	public void name(String name) {
		setConfigName(name);
	}

	/**
	 * @deprecated Use {@link #getMainClass()} instead.
	 */
	@Deprecated
	public void defaultMainClass(String cls) {
		setDefaultMainClass(cls);
	}

	/**
	 * @deprecated Use {@link #getRunDirectory()} instead.
	 */
	@Deprecated
	public void runDir(String dir) {
		setRunDir(dir);
	}

	/**
	 * @deprecated Use {@link #getJvmArguments()} instead.
	 */
	@Deprecated
	public void vmArg(String arg) {
		getJvmArguments().add(arg);
	}

	/**
	 * @deprecated Use {@link #getJvmArguments()} instead.
	 */
	@Deprecated
	public void vmArgs(String... args) {
		getJvmArguments().addAll(Arrays.asList(args));
	}

	/**
	 * @deprecated Use {@link #getJvmArguments()} instead.
	 */
	@Deprecated
	public void vmArgs(Collection<String> args) {
		getJvmArguments().addAll(args);
	}

	/**
	 * @deprecated Use {@link #getProgramArgs()} instead.
	 */
	@Deprecated
	public void programArg(String arg) {
		getProgramArguments().add(arg);
	}

	/**
	 * @deprecated Use {@link #getProgramArgs()} instead.
	 */
	@Deprecated
	public void programArgs(String... args) {
		getProgramArguments().addAll(Arrays.asList(args));
	}

	/**
	 * @deprecated Use {@link #getProgramArgs()} instead.
	 */
	@Deprecated
	public void programArgs(Collection<String> args) {
		getProgramArguments().addAll(args);
	}

	/**
	 * @deprecated Use {@link #getSourceSet()} instead.
	 */
	@Deprecated
	public void source(SourceSet source) {
		setSource(source);
	}

	/**
	 * @deprecated Use {@link #getSourceSet()} instead.
	 */
	@Deprecated
	public void source(String source) {
		setSource(proj -> SourceSetHelper.getSourceSetByName(source, proj));
	}

	/**
	 * @deprecated Use {@link #getGenerateRunConfig()} instead.
	 */
	@Deprecated
	public void ideConfigGenerated(boolean ideConfigGenerated) {
		getGenerateRunConfig().set(ideConfigGenerated);
	}

	/**
	 * @deprecated Use {@link #getEnvironmentVars()} instead.
	 */
	@Deprecated
	public Map<String, Object> getEnvironmentVariables() {
		return getEnvironmentVars().get();
	}

	/**
	 * @deprecated Use {@link #getEnvironmentVars()} instead.
	 */
	@Deprecated
	public void environmentVariable(String name, Object value) {
		getEnvironmentVars().put(name, value);
	}

	/**
	 * @deprecated Use {@link #getSystemProperties()} instead.
	 */
	@Deprecated
	public void property(String name, String value) {
		getSystemProperties().put(name, value);
	}

	/**
	 * @deprecated Use {@link #getSystemProperties()} instead.
	 */
	@Deprecated
	public void property(String name) {
		getSystemProperties().put(name, "");
	}

	/**
	 * @deprecated Use {@link #getSystemProperties()} instead.
	 */
	@Deprecated
	public void properties(Map<String, String> props) {
		getSystemProperties().putAll(props);
	}

	/**
	 * @deprecated No replacement
	 */
	@Deprecated
	public void startFirstThread() {
		if (Platform.CURRENT.getOperatingSystem().isMacOS()) {
			vmArg("-XstartOnFirstThread");
		}
	}

	/**
	 * @deprecated No replacement
	 */
	@Deprecated
	public void makeRunDir() {
		File file = getRunDirectory().getAsFile().get();

		if (!file.exists()) {
			file.mkdir();
		}
	}

	/**
	 * @deprecated Use {@link #getGenerateRunConfig()} instead.
	 */
	@Deprecated
	public boolean isIdeConfigGenerated() {
		return getGenerateRunConfig().get();
	}

	/**
	 * @deprecated Use {@link #getGenerateRunConfig()} instead.
	 */
	@Deprecated
	public void setIdeConfigGenerated(boolean ideConfigGenerated) {
		getGenerateRunConfig().set(ideConfigGenerated);
	}
}
