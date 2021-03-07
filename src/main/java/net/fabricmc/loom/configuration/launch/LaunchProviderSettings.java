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

package net.fabricmc.loom.configuration.launch;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.gradle.api.Named;
import org.gradle.api.Project;

public class LaunchProviderSettings implements Named {
	private final String name;
	private List<Map.Entry<String, String>> properties = new ArrayList<>();
	private List<String> arguments = new ArrayList<>();

	public LaunchProviderSettings(Project project, String name) {
		this.name = name;
	}

	@Override
	public String getName() {
		return name;
	}

	public void arg(String argument) {
		this.arguments.add(argument);
	}

	public void arg(String... arguments) {
		this.arguments.addAll(Arrays.asList(arguments));
	}

	public void arg(Collection<String> arguments) {
		this.arguments.addAll(arguments);
	}

	public void property(String key, String value) {
		this.properties.add(new AbstractMap.SimpleEntry<>(key, value));
	}

	public void properties(Map<String, String> arguments) {
		this.properties.addAll(arguments.entrySet());
	}

	public List<Map.Entry<String, String>> getProperties() {
		return properties;
	}

	public List<String> getArguments() {
		return arguments;
	}
}
