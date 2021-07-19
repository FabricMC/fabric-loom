/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2021 FabricMC
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

import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.MirrorUtil;

import org.gradle.api.Project;

public class MirrorConfiguration {
	public static void setup(Project project) {
		if (!(project.hasProperty(Constants.PROP_LOOM_LIBRARIES_BASE)
				|| project.hasProperty(Constants.PROP_LOOM_RESOURCES_BASE)
				|| project.hasProperty(Constants.PROP_LOOM_VERSION_MANIFESTS)
				|| project.hasProperty(Constants.PROP_EXPERIMENTAL_VERSIONS))) {
			return;
		}

		String librariesBaseMirrors = project.hasProperty(Constants.PROP_LOOM_LIBRARIES_BASE)
				? String.valueOf(project.property(Constants.PROP_LOOM_LIBRARIES_BASE)) : Constants.LIBRARIES_BASE;
		String resourcesBaseMirrors = project.hasProperty(Constants.PROP_LOOM_RESOURCES_BASE)
				? String.valueOf(project.property(Constants.PROP_LOOM_RESOURCES_BASE)) : Constants.RESOURCES_BASE;
		String versionManifests = project.hasProperty(Constants.PROP_LOOM_VERSION_MANIFESTS)
				? String.valueOf(project.property(Constants.PROP_LOOM_VERSION_MANIFESTS)) : Constants.VERSION_MANIFESTS;
		String experimentalVersion = project.hasProperty(Constants.PROP_EXPERIMENTAL_VERSIONS)
				? String.valueOf(project.property(Constants.PROP_EXPERIMENTAL_VERSIONS)) : Constants.EXPERIMENTAL_VERSIONS;

		MirrorUtil.setupMirror(librariesBaseMirrors, resourcesBaseMirrors, versionManifests, experimentalVersion);
	}
}
