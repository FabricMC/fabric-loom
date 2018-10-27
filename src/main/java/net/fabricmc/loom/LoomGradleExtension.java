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

package net.fabricmc.loom;

import net.fabricmc.loom.util.Constants;
import org.gradle.api.Project;

import java.io.File;

public class LoomGradleExtension {
	public String version;
	public String runDir = "run";
	public String fabricVersion;
	public String pomfVersion;
	public String refmapName;
	public String jarMapper = Constants.JAR_MAPPER_ENIGMA; // enigma, tiny
	public boolean localMappings = false;

	//Not to be set in the build.gradle
	public Project project;

	public String getVersionString() {
		if (isModWorkspace()) {
			return version + "-" + fabricVersion;
		}
		return version;
	}

	public boolean isModWorkspace() {
		return fabricVersion != null && !fabricVersion.isEmpty();
	}

	public File getUserCache() {
		File userCache = new File(project.getGradle().getGradleUserHomeDir(), "caches" + File.separator + "fabric-loom");
		if (!userCache.exists()) {
			userCache.mkdirs();
		}
		return userCache;
	}

	public boolean hasPomf(){
		if (localMappings) {
			return true;
		}
		return pomfVersion != null && !pomfVersion.isEmpty();
	}
}
