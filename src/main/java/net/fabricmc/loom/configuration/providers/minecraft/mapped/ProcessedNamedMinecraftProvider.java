/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021-2022 FabricMC
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

package net.fabricmc.loom.configuration.providers.minecraft.mapped;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.processors.MinecraftJarProcessorManager;
import net.fabricmc.loom.configuration.processors.ProcessorContextImpl;
import net.fabricmc.loom.configuration.providers.minecraft.MergedMinecraftProvider;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftJar;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftProvider;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftSourceSets;
import net.fabricmc.loom.configuration.providers.minecraft.SingleJarEnvType;
import net.fabricmc.loom.configuration.providers.minecraft.SingleJarMinecraftProvider;
import net.fabricmc.loom.configuration.providers.minecraft.SplitMinecraftProvider;

public abstract class ProcessedNamedMinecraftProvider<M extends MinecraftProvider, P extends NamedMinecraftProvider<M>> extends NamedMinecraftProvider<M> {
	private final P parentMinecraftProvider;
	private final MinecraftJarProcessorManager jarProcessorManager;
	private final String projectMappedName;
	private final Path projectMappedDir;

	public ProcessedNamedMinecraftProvider(P parentMinecraftProvide, MinecraftJarProcessorManager jarProcessorManager) {
		super(parentMinecraftProvide.getProject(), parentMinecraftProvide.getMinecraftProvider());
		this.parentMinecraftProvider = parentMinecraftProvide;
		this.jarProcessorManager = Objects.requireNonNull(jarProcessorManager);

		this.projectMappedName = "minecraft-project-%s-".formatted(getProject().getPath().replace(':', '@'));

		final LoomGradleExtension extension = LoomGradleExtension.get(getProject());
		this.projectMappedDir = extension.getFiles().getRootProjectPersistentCache().toPath()
				.resolve(getMinecraftProvider().minecraftVersion())
				.resolve(extension.getMappingsProvider().mappingsIdentifier());
	}

	@Override
	public void provide(boolean applyDependencies) throws Exception {
		parentMinecraftProvider.provide(false);

		boolean requiresProcessing = parentMinecraftProvider.getMinecraftJarPaths().stream()
				.anyMatch(jarProcessorManager::requiresProcessingJar);

		if (requiresProcessing) {
			processJars();
		}

		if (applyDependencies) {
			applyDependencies();
		}
	}

	private void processJars() throws IOException {
		try {
			Files.createDirectories(projectMappedDir);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to create project mapped dir", e);
		}

		for (MinecraftJar minecraftJar : parentMinecraftProvider.getMinecraftJars()) {
			Path inputJar = minecraftJar.getPath();
			final Path outputJar = getProcessedPath(inputJar);
			deleteSimilarJars(outputJar);

			Files.copy(inputJar, outputJar, StandardCopyOption.REPLACE_EXISTING);

			jarProcessorManager.processJar(outputJar, ProcessorContextImpl.create(getProject(), minecraftJar));
		}
	}

	private void applyDependencies() {
		final List<String> dependencyTargets = parentMinecraftProvider.getDependencyTargets();

		if (dependencyTargets.isEmpty()) {
			return;
		}

		MinecraftSourceSets.get(getProject()).applyDependencies(
				(configuration, name) -> getProject().getDependencies().add(configuration, getDependencyNotation(name)),
				dependencyTargets
		);
	}

	private void deleteSimilarJars(Path jar) throws IOException {
		Files.deleteIfExists(jar);

		for (Path path : Files.list(jar.getParent()).filter(Files::isRegularFile)
				.filter(path -> path.getFileName().startsWith(jar.getFileName().toString().replace(".jar", ""))).toList()) {
			Files.deleteIfExists(path);
		}
	}

	@Override
	protected String getName(String name) {
		return "%s%s-%s".formatted(projectMappedName, name, getTargetNamespace().toString());
	}

	@Override
	public Path getJar(String name) {
		// Something has gone wrong if this gets called.
		throw new UnsupportedOperationException();
	}

	@Override
	public List<RemappedJars> getRemappedJars() {
		throw new UnsupportedOperationException();
	}

	@Override
	public List<Path> getMinecraftJarPaths() {
		return getParentMinecraftProvider().getMinecraftJarPaths().stream()
				.map(this::getProcessedPath)
				.toList();
	}

	public P getParentMinecraftProvider() {
		return parentMinecraftProvider;
	}

	public Path getProcessedPath(Path input) {
		return projectMappedDir.resolve(input.getFileName().toString().replace("minecraft-", projectMappedName));
	}

	public static final class MergedImpl extends ProcessedNamedMinecraftProvider<MergedMinecraftProvider, NamedMinecraftProvider.MergedImpl> implements Merged {
		public MergedImpl(NamedMinecraftProvider.MergedImpl parentMinecraftProvide, MinecraftJarProcessorManager jarProcessorManager) {
			super(parentMinecraftProvide, jarProcessorManager);
		}

		@Override
		public Path getMergedJar() {
			return getProcessedPath(getParentMinecraftProvider().getMergedJar());
		}
	}

	public static final class SplitImpl extends ProcessedNamedMinecraftProvider<SplitMinecraftProvider, NamedMinecraftProvider.SplitImpl> implements Split {
		public SplitImpl(NamedMinecraftProvider.SplitImpl parentMinecraftProvide, MinecraftJarProcessorManager jarProcessorManager) {
			super(parentMinecraftProvide, jarProcessorManager);
		}

		@Override
		public Path getCommonJar() {
			return getProcessedPath(getParentMinecraftProvider().getCommonJar());
		}

		@Override
		public Path getClientOnlyJar() {
			return getProcessedPath(getParentMinecraftProvider().getClientOnlyJar());
		}
	}

	public static final class SingleJarImpl extends ProcessedNamedMinecraftProvider<SingleJarMinecraftProvider, NamedMinecraftProvider.SingleJarImpl> implements SingleJar {
		private final SingleJarEnvType env;

		private SingleJarImpl(NamedMinecraftProvider.SingleJarImpl parentMinecraftProvide, MinecraftJarProcessorManager jarProcessorManager, SingleJarEnvType env) {
			super(parentMinecraftProvide, jarProcessorManager);
			this.env = env;
		}

		public static ProcessedNamedMinecraftProvider.SingleJarImpl server(NamedMinecraftProvider.SingleJarImpl parentMinecraftProvide, MinecraftJarProcessorManager jarProcessorManager) {
			return new ProcessedNamedMinecraftProvider.SingleJarImpl(parentMinecraftProvide, jarProcessorManager, SingleJarEnvType.SERVER);
		}

		public static ProcessedNamedMinecraftProvider.SingleJarImpl client(NamedMinecraftProvider.SingleJarImpl parentMinecraftProvide, MinecraftJarProcessorManager jarProcessorManager) {
			return new ProcessedNamedMinecraftProvider.SingleJarImpl(parentMinecraftProvide, jarProcessorManager, SingleJarEnvType.CLIENT);
		}

		@Override
		public Path getEnvOnlyJar() {
			return getProcessedPath(getParentMinecraftProvider().getEnvOnlyJar());
		}

		@Override
		public SingleJarEnvType env() {
			return env;
		}
	}
}
