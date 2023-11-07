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

import java.io.File;
import java.util.Objects;

import org.gradle.api.Project;

public final class LoomFilesProjectImpl extends LoomFilesBaseImpl {
	private final Project project;

	public LoomFilesProjectImpl(Project project) {
		this.project = Objects.requireNonNull(project);
	}

	@Override
	protected File getGradleUserHomeDir() {
		return project.getGradle().getGradleUserHomeDir();
	}

	@Override
	protected File getRootDir() {
		return project.getRootDir();
	}

	@Override
	protected File getProjectDir() {
		return project.getProjectDir();
	}

	@Override
	protected File getBuildDir() {
		return project.getLayout().getBuildDirectory().getAsFile().get();
	}
}
