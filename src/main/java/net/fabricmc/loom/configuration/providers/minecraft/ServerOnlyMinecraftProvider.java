/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022 FabricMC
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.gradle.api.Project;

import net.fabricmc.loom.configuration.providers.BundleMetadata;
import net.fabricmc.tinyremapper.NonClassCopyMode;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;

public final class ServerOnlyMinecraftProvider extends MinecraftProvider {
	private Path minecraftServerOnlyJar;

	public ServerOnlyMinecraftProvider(Project project) {
		super(project);
	}

	@Override
	protected void initFiles() {
		super.initFiles();

		minecraftServerOnlyJar = path("minecraft-server-only.jar");
	}

	@Override
	public List<Path> getMinecraftJars() {
		return List.of(minecraftServerOnlyJar);
	}

	@Override
	public void provide() throws Exception {
		super.provide();

		boolean requiresRefresh = isRefreshDeps() || Files.notExists(minecraftServerOnlyJar);

		if (!requiresRefresh) {
			return;
		}

		BundleMetadata serverBundleMetadata = getServerBundleMetadata();

		if (serverBundleMetadata == null) {
			throw new UnsupportedOperationException("Only Minecraft versions using a bundled server jar support server only configuration, please use a merged jar setup for this version of minecraft");
		}

		extractBundledServerJar();
		final Path serverJar = getMinecraftExtractedServerJar().toPath();

		TinyRemapper remapper = null;

		try {
			remapper = TinyRemapper.newRemapper().build();

			Files.deleteIfExists(minecraftServerOnlyJar);

			// Pass through tiny remapper to fix the meta-inf
			try (OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(minecraftServerOnlyJar).build()) {
				outputConsumer.addNonClassFiles(serverJar, NonClassCopyMode.FIX_META_INF, remapper);
				remapper.readInputs(serverJar);
				remapper.apply(outputConsumer);
			}
		} catch (Exception e) {
			Files.deleteIfExists(minecraftServerOnlyJar);
			throw new RuntimeException("Failed to process server only jar", e);
		} finally {
			if (remapper != null) {
				remapper.finish();
			}
		}
	}

	public Path getMinecraftServerOnlyJar() {
		return minecraftServerOnlyJar;
	}
}
