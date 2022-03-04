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

import java.io.File;

import org.gradle.api.Named;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetOutput;
import org.jetbrains.annotations.ApiStatus;

/**
 * A {@link Named} object for setting mod realted values. The {@link Named#getName()} should match the modid.
 */
@ApiStatus.Experimental
public abstract class ModSettings implements Named {
	/**
	 * List of classpath directories, used to populate the `fabric.classPathGroups` Fabric Loader system property.
	 */
	public abstract ListProperty<File> getClasspath();

	/**
	 * Mark a {@link SourceSet} output directories part of the named mod.
	 */
	public void sourceSet(SourceSet sourceSet) {
		final SourceSetOutput output = sourceSet.getOutput();
		final File resources = output.getResourcesDir();

		getClasspath().addAll(output.getClassesDirs().getFiles());

		if (resources != null) {
			getClasspath().add(resources);
		}
	}
}
