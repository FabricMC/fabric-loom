/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2024 FabricMC
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

import java.nio.file.Path;
import java.util.List;

import net.fabricmc.loom.configuration.ConfigContext;

/**
 * Minecraft versions prior to 1.3 obfuscate the server and client jars differently.
 * The obfuscated jars must be provided separately, and can be merged after remapping.
 */
public final class LegacyMinecraftProvider extends MinecraftProvider {
	private final SingleJarMinecraftProvider.Server serverMinecraftProvider;
	private final SingleJarMinecraftProvider.Client clientMinecraftProvider;

	public LegacyMinecraftProvider(MinecraftMetadataProvider metadataProvider, ConfigContext configContext) {
		super(metadataProvider, configContext);
		serverMinecraftProvider = SingleJarMinecraftProvider.server(metadataProvider, configContext);
		clientMinecraftProvider = SingleJarMinecraftProvider.client(metadataProvider, configContext);
	}

	public SingleJarMinecraftProvider.Server getServerMinecraftProvider() {
		return serverMinecraftProvider;
	}

	public SingleJarMinecraftProvider.Client getClientMinecraftProvider() {
		return clientMinecraftProvider;
	}

	@Override
	public void provide() throws Exception {
		// we must first call super.provide() to load the version info
		// then we verify that this version allows merging the obfuscated jars

		super.provide();

		if (canMergeJars()) {
			throw new UnsupportedOperationException("The legacy-merged jar configuration should only be used for versions where the obfuscated jars cannot be merged - please select the merged jar configuration or use the client-only or server-only jar configuration!");
		}

		serverMinecraftProvider.provide();
		clientMinecraftProvider.provide();
	}

	@Override
	public List<Path> getMinecraftJars() {
		return List.of(
			serverMinecraftProvider.getMinecraftEnvOnlyJar(),
			clientMinecraftProvider.getMinecraftEnvOnlyJar()
		);
	}
}
