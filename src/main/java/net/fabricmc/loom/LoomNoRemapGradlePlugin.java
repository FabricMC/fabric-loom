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

package net.fabricmc.loom;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * A marker plugin to indicate to the main loom plugin not to setup for remapping.
 */
public class LoomNoRemapGradlePlugin implements Plugin<Project> {
	public static final String NAME = "net.fabricmc.fabric-loom-no-remap-experimental";

	@Override
	public void apply(Project target) {
		if (target.getPluginManager().hasPlugin(LoomGradlePlugin.NAME)) {
			throw new IllegalStateException(NAME + " must be applied before " + LoomGradlePlugin.NAME);
		}

		target.getPlugins().apply(LoomGradlePlugin.NAME);
	}
}
