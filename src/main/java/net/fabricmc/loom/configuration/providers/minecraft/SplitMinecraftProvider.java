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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.gradle.api.Project;

import net.fabricmc.loom.configuration.providers.BundleMetadata;

public final class SplitMinecraftProvider extends MinecraftProvider {
	private File minecraftClientOnlyJar;
	private File minecraftCommonJar;

	public SplitMinecraftProvider(Project project) {
		super(project);
	}

	@Override
	protected void initFiles() {
		super.initFiles();

		minecraftClientOnlyJar = file("minecraft-client-only.jar");
		minecraftCommonJar = file("minecraft-common.jar");
	}

	@Override
	public List<Path> getMinecraftJars() {
		return List.of(minecraftClientOnlyJar.toPath(), minecraftCommonJar.toPath());
	}

	@Override
	public void provide() throws Exception {
		super.provide();

		boolean requiresRefresh = isRefreshDeps() || !minecraftClientOnlyJar.exists() || !minecraftCommonJar.exists();

		if (!requiresRefresh) {
			return;
		}

		BundleMetadata serverBundleMetadata = getServerBundleMetadata();

		if (serverBundleMetadata == null) {
			// TODO possibly look into supporting 1.17, the bundled server gives us a much cleaner server jar to work with however.
			throw new UnsupportedOperationException("Only bundled server jars can be split, please use a merged jar setup for this version of minecraft");
		}

		extractBundledServerJar();

		final Path clientJar = getMinecraftClientJar().toPath();
		final Path serverJar = getMinecraftExtractedServerJar().toPath();

		try (MinecraftJarSplitter jarSplitter = new MinecraftJarSplitter(clientJar, serverJar)) {
			// Required for loader to compute the version info also useful to have in both jars.
			jarSplitter.sharedEntry("version.json");
			jarSplitter.forcedClientEntry("assets/.mcassetsroot");

			jarSplitter.split(minecraftClientOnlyJar.toPath(), minecraftCommonJar.toPath());
		} catch (Exception e) {
			Files.deleteIfExists(minecraftClientOnlyJar.toPath());
			Files.deleteIfExists(minecraftCommonJar.toPath());

			throw new RuntimeException("Failed to split minecraft", e);
		}

		// TODO split: cleanup on failiure
	}

	public File getMinecraftClientOnlyJar() {
		return minecraftClientOnlyJar;
	}

	public File getMinecraftCommonJar() {
		return minecraftCommonJar;
	}
}
