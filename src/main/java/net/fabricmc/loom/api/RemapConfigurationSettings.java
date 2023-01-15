/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022 FabricMC
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

import javax.inject.Inject;

import org.gradle.api.Named;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.SourceSet;
import org.jetbrains.annotations.NotNull;

/**
 * A {@link Named} object for configuring "proxy" configurations that remap artifacts.
 */
public abstract class RemapConfigurationSettings implements Named {
	private final String name;

	@Inject
	public RemapConfigurationSettings(String name) {
		this.name = name;

		getSourceSet().finalizeValueOnRead();
		getCompileClasspathConfiguration().finalizeValueOnRead();
		getMappedCompileClasspathConfiguration().finalizeValueOnRead();
		getMappedClientCompileClasspathConfiguration().finalizeValueOnRead();
		getRuntimeClasspathConfiguration().finalizeValueOnRead();
		getMappedRuntimeClasspathConfiguration().finalizeValueOnRead();
		getMappedClientRuntimeClasspathConfiguration().finalizeValueOnRead();
	}

	@Override
	public @NotNull String getName() {
		return name;
	}

	/**
	 * @return The target source set
	 */
	public abstract Property<SourceSet> getSourceSet();

	public abstract Property<Configuration> getCompileClasspathConfiguration();
	public abstract Property<Configuration> getMappedCompileClasspathConfiguration();
	public abstract Property<Configuration> getMappedClientCompileClasspathConfiguration();
	public abstract Property<Configuration> getRuntimeClasspathConfiguration();
	public abstract Property<Configuration> getMappedRuntimeClasspathConfiguration();
	public abstract Property<Configuration> getMappedClientRuntimeClasspathConfiguration();

	public enum PublishingMode {
		RUNTIME_ONLY // still used by test TODO remove
	}

	@Inject
	protected abstract Project getProject();
}
