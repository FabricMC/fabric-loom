/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021 FabricMC
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

package net.fabricmc.loom.extension;

import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.util.PatternSet;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.MixinApExtensionAPI;

public abstract class MixinApExtensionApiImpl implements MixinApExtensionAPI {
	protected abstract Project getProject();
	protected abstract PatternSet add0(SourceSet sourceSet, String refmapName);

	@Override
	public void add(SourceSet sourceSet, String refmapName, Action<PatternSet> action) {
		PatternSet pattern = add0(sourceSet, refmapName);
		action.execute(pattern);
	}

	@Override
	public void add(SourceSet sourceSet, String refmapName) {
		add(sourceSet, refmapName, x -> { });
	}

	@Override
	public void add(String sourceSetName, String refmapName, Action<PatternSet> action) {
		// try to find sourceSet with name sourceSetName in this project
		SourceSet sourceSet = getProject().getConvention().getPlugin(JavaPluginConvention.class)
				.getSourceSets().findByName(sourceSetName);

		if (sourceSet == null) {
			throw new InvalidUserDataException("No sourceSet " + sourceSetName + " was found");
		}

		PatternSet pattern = add0(sourceSet, refmapName);
		action.execute(pattern);
	}

	@Override
	public void add(String sourceSetName, String refmapName) {
		add(sourceSetName, refmapName, x -> { });
	}

	@Override
	public void add(SourceSet sourceSet, Action<PatternSet> action) {
		LoomGradleExtension extension = LoomGradleExtension.get(getProject());
		add(sourceSet, extension.getRefmapName(), action);
	}

	@Override
	public void add(SourceSet sourceSet) {
		add(sourceSet, x -> { });
	}

	@Override
	public void add(String sourceSetName, Action<PatternSet> action) {
		LoomGradleExtension extension = LoomGradleExtension.get(getProject());
		add(sourceSetName, extension.getRefmapName(), action);
	}

	@Override
	public void add(String sourceSetName) {
		add(sourceSetName, x -> { });
	}

	// This is here to ensure that LoomGradleExtensionApiImpl compiles without any unimplemented methods
	private final class EnsureCompile extends MixinApExtensionApiImpl {
		private EnsureCompile() {
			super();
			throw new RuntimeException();
		}

		@Override
		protected Project getProject() {
			throw new RuntimeException("Yeah... something is really wrong");
		}

		@Override
		protected PatternSet add0(SourceSet sourceSet, String refmapName) {
			throw new RuntimeException("Yeah... something is really wrong");
		}
	}
}
