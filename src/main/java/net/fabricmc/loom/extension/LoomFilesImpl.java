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

import net.fabricmc.loom.configuration.providers.MinecraftProvider;

public final class LoomFilesImpl implements LoomFiles {
	private final Project project;

	private final File userCache;
	private final File rootProjectPersistentCache;
	private final File projectPersistentCache;
	private final File projectBuildCache;
	private final File remappedModCache;
	private final File nativesJarStore;

	public LoomFilesImpl(Project project) {
		this.project = project;

		this.userCache = createFile(project.getGradle().getGradleUserHomeDir(), "caches" + File.separator + "fabric-loom");
		this.rootProjectPersistentCache = createFile(project.getRootProject().file(".gradle"), "loom-cache");
		this.projectPersistentCache = createFile(project.file(".gradle"), "loom-cache");
		this.projectBuildCache = createFile(project.getBuildDir(), "loom-cache");
		this.remappedModCache = createFile(getRootProjectPersistentCache(), "remapped_mods");
		this.nativesJarStore = createFile(getUserCache(), "natives/jars");
	}

	private File createFile(File parent, String child) {
		File file = new File(parent, child);

		if (!file.exists()) {
			file.mkdirs();
		}

		return file;
	}

	@Override
	public File getUserCache() {
		return userCache;
	}

	@Override
	public File getRootProjectPersistentCache() {
		return rootProjectPersistentCache;
	}

	@Override
	public File getProjectPersistentCache() {
		return projectPersistentCache;
	}

	@Override
	public File getProjectBuildCache() {
		return projectBuildCache;
	}

	@Override
	public File getRemappedModCache() {
		return remappedModCache;
	}

	@Override
	public File getNativesJarStore() {
		return nativesJarStore;
	}

	@Override
	public boolean hasCustomNatives() {
		return project.getProperties().get("fabric.loom.natives.dir") != null;
	}

	@Override
	public File getNativesDirectory(MinecraftProvider minecraftProvider) {
		if (hasCustomNatives()) {
			return new File((String) project.property("fabric.loom.natives.dir"));
		}

		File natives = new File(getUserCache(), "natives/" + minecraftProvider.minecraftVersion());

		if (!natives.exists()) {
			natives.mkdirs();
		}

		return natives;
	}

	@Override
	public File getDefaultLog4jConfigFile() {
		return new File(getProjectPersistentCache(), "log4j.xml");
	}

	@Override
	public File getDevLauncherConfig() {
		return new File(getProjectPersistentCache(), "launch.cfg");
	}

	@Override
	public File getUnpickLoggingConfigFile() {
		return new File(getProjectPersistentCache(), "unpick-logging.properties");
	}
}
