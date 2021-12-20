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

package net.fabricmc.loom.configuration.providers.minecraft;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

import org.gradle.api.Project;

import net.fabricmc.loom.util.HashedDownloadUtil;
import net.fabricmc.stitch.merge.JarMerger;

public final class MergedMinecraftProvider extends MinecraftProvider {
	private File minecraftMergedJar;

	public MergedMinecraftProvider(Project project) {
		super(project);
	}

	@Override
	protected void initFiles() {
		super.initFiles();
		minecraftMergedJar = file("minecraft-merged.jar");
	}

	@Override
	public void provide(DependencyInfo dependency, Consumer<Runnable> postPopulationScheduler) throws Exception {
		super.provide(dependency, postPopulationScheduler);

		if (!minecraftMergedJar.exists() || isRefreshDeps()) {
			try {
				mergeJars();
			} catch (Throwable e) {
				HashedDownloadUtil.delete(getMinecraftClientJar());
				HashedDownloadUtil.delete(getMinecraftServerJar());
				minecraftMergedJar.delete();

				getProject().getLogger().error("Could not merge JARs! Deleting source JARs - please re-run the command and move on.", e);
				throw e;
			}
		}
	}

	private void mergeJars() throws IOException {
		getLogger().info(":merging jars");

		File jarToMerge = getMinecraftServerJar();

		if (getServerBundleMetadata() != null) {
			extractBundledServerJar();
			jarToMerge = getMinecraftExtractedServerJar();
		}

		Objects.requireNonNull(jarToMerge, "Cannot merge null input jar?");

		try (JarMerger jarMerger = new JarMerger(getMinecraftClientJar(), jarToMerge, minecraftMergedJar)) {
			jarMerger.enableSyntheticParamsOffset();
			jarMerger.merge();
		}
	}

	public File getMergedJar() {
		return minecraftMergedJar;
	}
}
