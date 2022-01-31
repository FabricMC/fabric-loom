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

public final class SingleJarMinecraftProvider extends MinecraftProvider {
	private final Environment environment;

	private Path minecraftEnvOnlyJar;

	private SingleJarMinecraftProvider(Project project, Environment environment) {
		super(project);
		this.environment = environment;
	}

	public static SingleJarMinecraftProvider server(Project project) {
		return new SingleJarMinecraftProvider(project, new Server());
	}

	public static SingleJarMinecraftProvider client(Project project) {
		return new SingleJarMinecraftProvider(project, new Client());
	}

	@Override
	protected void initFiles() {
		super.initFiles();

		minecraftEnvOnlyJar = path("minecraft-%s-only.jar".formatted(environment.name()));
	}

	@Override
	public List<Path> getMinecraftJars() {
		return List.of(minecraftEnvOnlyJar);
	}

	@Override
	public void provide() throws Exception {
		super.provide();

		boolean requiresRefresh = isRefreshDeps() || Files.notExists(minecraftEnvOnlyJar);

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
			throw new RuntimeException("Failed to process %s only jar".formatted(environment.name()), e);
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
		String name();

		Path getInputJar(SingleJarMinecraftProvider provider) throws Exception;
	}

	private static final class Server implements Environment {
		@Override
		public String name() {
			return "server";
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
		public String name() {
			return "client";
		}

		@Override
		public Path getInputJar(SingleJarMinecraftProvider provider) throws Exception {
			return provider.getMinecraftClientJar().toPath();
		}
	}
}
