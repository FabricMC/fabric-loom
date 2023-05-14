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

package net.fabricmc.loom.configuration.ide.idea;

import java.util.Objects;

import org.gradle.api.Project;

import net.fabricmc.loom.util.gradle.SourceSetReference;

public class IdeaUtils {
	public static boolean isIdeaSync() {
		return Boolean.parseBoolean(System.getProperty("idea.sync.active", "false"));
	}

	public static String getIdeaVersion() {
		return Objects.requireNonNull(System.getProperty("idea.version"), "Could not get idea version");
	}

	// 2021.3 or newer
	public static boolean supportsCustomizableClasspath() {
		final String[] split = getIdeaVersion().split("\\.");
		final int major = Integer.parseInt(split[0]);
		final int minor = Integer.parseInt(split[1]);
		return major > 2021 || (major == 2021 && minor >= 3);
	}

	public static String getIdeaModuleName(SourceSetReference reference) {
		Project project = reference.project();
		String module = project.getName() + "." + reference.sourceSet().getName();

		while ((project = project.getParent()) != null) {
			module = project.getName() + "." + module;
		}

		return module.replace(' ', '_');
	}
}
