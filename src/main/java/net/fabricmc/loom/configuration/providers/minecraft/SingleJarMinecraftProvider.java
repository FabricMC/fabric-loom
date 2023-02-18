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

import net.fabricmc.loom.configuration.ConfigContext;
import net.fabricmc.loom.configuration.providers.BundleMetadata;
import net.fabricmc.tinyremapper.NonClassCopyMode;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;

public final class SingleJarMinecraftProvider extends MinecraftProvider {
	private final Environment environment;

	private Path minecraftEnvOnlyJar;

	private SingleJarMinecraftProvider(ConfigContext configContext, Environment environment) {
		super(configContext);
		this.environment = environment;
	}

	public static SingleJarMinecraftProvider server(ConfigContext configContext) {
		return new SingleJarMinecraftProvider(configContext, new Server());
	}

	public static SingleJarMinecraftProvider client(ConfigContext configContext) {
		return new SingleJarMinecraftProvider(configContext, new Client());
	}

	@Override
	protected void initFiles() {
		super.initFiles();

		minecraftEnvOnlyJar = path("minecraft-%s-only.jar".formatted(environment.type()));
	}

	@Override
	public List<Path> getMinecraftJars() {
		return List.of(minecraftEnvOnlyJar);
	}

	@Override
	public void provide() throws Exception {
		super.provide();

		// Server only JARs are supported on any version, client only JARs are pretty much useless after 1.3.
		if (provideClient() && getVersionInfo().isVersionOrNewer("2012-07-25T22:00:00+00:00" /* 1.3 release date */)) {
			getProject().getLogger().warn("Using `clientOnlyMinecraftJar()` is not recommended for Minecraft versions 1.3 or newer.");
		}

		boolean requiresRefresh = getExtension().refreshDeps() || Files.notExists(minecraftEnvOnlyJar);

		if (!requiresRefresh) {
			return;
		}

		final Path inputJar = environment.getInputJar(this);

		TinyRemapper remapper = null;

		try {
			remapper = TinyRemapper.newRemapper().build();

			Files.deleteIfExists(minecraftEnvOnlyJar);

			// Pass through tiny remapper to fix the meta-inf
			try (OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(minecraftEnvOnlyJar).build()) {
				outputConsumer.addNonClassFiles(inputJar, NonClassCopyMode.FIX_META_INF, remapper);
				remapper.readInputs(inputJar);
				remapper.apply(outputConsumer);
			}
		} catch (Exception e) {
			Files.deleteIfExists(minecraftEnvOnlyJar);
			throw new RuntimeException("Failed to process %s only jar".formatted(environment.type()), e);
		} finally {
			if (remapper != null) {
				remapper.finish();
			}
		}
	}

	@Override
	protected boolean provideClient() {
		return environment instanceof Client;
	}

	@Override
	protected boolean provideServer() {
		return environment instanceof Server;
	}

	public Path getMinecraftEnvOnlyJar() {
		return minecraftEnvOnlyJar;
	}

	private interface Environment {
		SingleJarEnvType type();

		Path getInputJar(SingleJarMinecraftProvider provider) throws Exception;
	}

	private static final class Server implements Environment {
		@Override
		public SingleJarEnvType type() {
			return SingleJarEnvType.SERVER;
		}

		@Override
		public Path getInputJar(SingleJarMinecraftProvider provider) throws Exception {
			BundleMetadata serverBundleMetadata = provider.getServerBundleMetadata();

			if (serverBundleMetadata == null) {
				return provider.getMinecraftServerJar().toPath();
			}

			provider.extractBundledServerJar();
			return provider.getMinecraftExtractedServerJar().toPath();
		}
	}

	private static final class Client implements Environment {
		@Override
		public SingleJarEnvType type() {
			return SingleJarEnvType.CLIENT;
		}

		@Override
		public Path getInputJar(SingleJarMinecraftProvider provider) throws Exception {
			return provider.getMinecraftClientJar().toPath();
		}
	}
}
