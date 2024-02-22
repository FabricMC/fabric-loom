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

import org.gradle.api.Project;
import org.gradle.api.initialization.Settings;

public interface LoomFiles {
	static LoomFiles create(Project project) {
		return new LoomFilesProjectImpl(project);
	}

	static LoomFiles create(Settings settings) {
		return new LoomFilesSettingsImpl(settings);
	}

	File getUserCache();
	File getRootProjectPersistentCache();
	File getProjectPersistentCache();
	File getProjectBuildCache();
	File getRemappedModCache();
	File getNativesDirectory(Project project);
	File getDefaultLog4jConfigFile();
	File getDevLauncherConfig();
	File getUnpickLoggingConfigFile();
	File getRemapClasspathFile();
	File getGlobalMinecraftRepo();
	File getLocalMinecraftRepo();
	File getDecompileCache(String version);
}
