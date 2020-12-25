/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016, 2017, 2018 FabricMC
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

package net.fabricmc.loom.configuration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.gradle.api.Named;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;

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
	 * The mode to run, which is the name of the run config in {@code fabric_installer.[method].json}.
	 */
	private String mode;

	/**
	 * The full name of the run configuration, i.e. 'Minecraft Client'.
	 * <p>
	 * By default this is determined from the base name.
	 */
	private String name;

	/**
	 * Whether the run config is for the client, i.e. uses natives and needs {@code -XstartOnFirstThread} on OSX.
	 */
	private Boolean client;

	/**
	 * The source set getter, which obtains the source set from the given project.
	 */
	private Function<Project, SourceSet> source;

	/**
	 * The base name of the run configuration, which is the name it is created with, i.e. 'client'
	 */
	private final String baseName;

	public RunConfigSettings(String baseName) {
		this.baseName = baseName;
		setMode(baseName);
		source("main");
	}

	@Override
	public String getName() {
		return baseName;
	}

	public List<String> getVmArgs() {
		return vmArgs;
	}

	public List<String> getProgramArgs() {
		return programArgs;
	}

	public String getMode() {
		return mode;
	}

	public void setMode(String mode) {
		this.mode = mode;
	}

	public String getConfigName() {
		return name;
	}

	public void setConfigName(String name) {
		this.name = name;
	}

	public boolean isClient() {
		String m = mode != null ? mode : baseName;
		return client != null ? client // Do not confuse users: detect client mode unless client mode is explicitly defined
				: m.toLowerCase().contains("client");
	}

	public void setClient(Boolean client) {
		this.client = client;
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

	public void mode(String mode) {
		setMode(mode);
	}

	public void name(String name) {
		setConfigName(name);
	}

	public void client(boolean client) {
		setClient(client);
	}

	public void server(boolean server) {
		setClient(!server);
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
		setSource(proj -> {
				JavaPluginConvention conv = proj.getConvention().getPlugin(JavaPluginConvention.class);
				return conv.getSourceSets().getByName(source);
		});
	}
}
