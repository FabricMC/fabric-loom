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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import net.fabricmc.loom.configuration.ConfigContext;

public final class MergedMinecraftProvider extends MinecraftProvider {
	private Path minecraftMergedJar;

	public MergedMinecraftProvider(ConfigContext configContext) {
		super(configContext);
	}

	@Override
	protected void initFiles() {
		super.initFiles();
		minecraftMergedJar = path("minecraft-merged.jar");
	}

	@Override
	public List<Path> getMinecraftJars() {
		return List.of(minecraftMergedJar);
	}

	@Override
	public void provide() throws Exception {
		// we must first call super.provide() to load the version info
		// then we verify that this version allows merging the obfuscated jars

		super.provide();

		if (!canMergeJars()) {
			throw new UnsupportedOperationException("This version does not allow merging the obfuscated jars - please select the legacy-merged jar configuration with appropriate mappings, or use the client-only or server-only jar configuration!");
		}

		if (!Files.exists(minecraftMergedJar) || getExtension().refreshDeps()) {
			try {
				mergeJars();
			} catch (Throwable e) {
				Files.deleteIfExists(getMinecraftClientJar().toPath());
				Files.deleteIfExists(getMinecraftServerJar().toPath());
				Files.deleteIfExists(minecraftMergedJar);

				getProject().getLogger().error("Could not merge JARs! Deleting source JARs - please re-run the command and move on.", e);
				throw e;
			}
		}
	}

	private void mergeJars() throws IOException {
		File minecraftClientJar = getMinecraftClientJar();
		File minecraftServerJar = getMinecraftServerJar();

		if (getServerBundleMetadata() != null) {
			extractBundledServerJar();
			minecraftServerJar = getMinecraftExtractedServerJar();
		}

		getLogger().info(":merging jars");

		mergeJars(minecraftClientJar, minecraftServerJar, minecraftMergedJar.toFile());
	}

	public static void mergeJars(File clientJar, File serverJar, File mergedJar) throws IOException {
		Objects.requireNonNull(clientJar, "Cannot merge null client jar?");
		Objects.requireNonNull(serverJar, "Cannot merge null server jar?");

		try (var jarMerger = new MinecraftJarMerger(clientJar, serverJar, mergedJar)) {
			jarMerger.enableSyntheticParamsOffset();
			jarMerger.merge();
		}
	}

	public Path getMergedJar() {
		return minecraftMergedJar;
	}
}
