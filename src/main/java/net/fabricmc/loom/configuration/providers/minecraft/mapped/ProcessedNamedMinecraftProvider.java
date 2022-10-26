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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import org.gradle.api.Project;

import net.fabricmc.loom.configuration.mods.dependency.LocalMavenHelper;
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

	public ProcessedNamedMinecraftProvider(P parentMinecraftProvide, MinecraftJarProcessorManager jarProcessorManager) {
		super(parentMinecraftProvide.getConfigContext(), parentMinecraftProvide.getMinecraftProvider());
		this.parentMinecraftProvider = parentMinecraftProvide;
		this.jarProcessorManager = Objects.requireNonNull(jarProcessorManager);
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

	@Override
	public MavenScope getMavenScope() {
		return MavenScope.LOCAL;
	}

	private void processJars() throws IOException {
		for (MinecraftJar minecraftJar : parentMinecraftProvider.getMinecraftJars()) {
			final MinecraftJar outputJar = getProcessedPath(minecraftJar);
			deleteSimilarJars(outputJar.getPath());

			final LocalMavenHelper mavenHelper = getMavenHelper(minecraftJar.getName());
			final Path outputPath = mavenHelper.copyToMaven(minecraftJar.getPath(), null);

			jarProcessorManager.processJar(outputPath, new ProcessorContextImpl(configContext, minecraftJar));
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
		final Path parent = jar.getParent();

		if (Files.notExists(parent)) {
			return;
		}

		for (Path path : Files.list(parent).filter(Files::isRegularFile)
				.filter(path -> path.getFileName().startsWith(jar.getFileName().toString().replace(".jar", ""))).toList()) {
			Files.deleteIfExists(path);
		}
	}

	@Override
	protected String getName(String name) {
		final Project project = getProject();

		if (project.getRootProject() == project) {
			return "minecraft-%s-project-root".formatted(name);
		}

		final String projectPath = project.getPath().replace(':', '@');
		return "minecraft-%s-project-%s".formatted(name, projectPath);
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
	public List<MinecraftJar> getMinecraftJars() {
		return getParentMinecraftProvider().getMinecraftJars().stream()
				.map(this::getProcessedPath)
				.toList();
	}

	public P getParentMinecraftProvider() {
		return parentMinecraftProvider;
	}

	public MinecraftJar getProcessedPath(MinecraftJar minecraftJar) {
		final Path path = getMavenHelper(minecraftJar.getName()).getOutputFile(null);
		return minecraftJar.forPath(path);
	}

	public static final class MergedImpl extends ProcessedNamedMinecraftProvider<MergedMinecraftProvider, NamedMinecraftProvider.MergedImpl> implements Merged {
		public MergedImpl(NamedMinecraftProvider.MergedImpl parentMinecraftProvide, MinecraftJarProcessorManager jarProcessorManager) {
			super(parentMinecraftProvide, jarProcessorManager);
		}

		@Override
		public MinecraftJar getMergedJar() {
			return getProcessedPath(getParentMinecraftProvider().getMergedJar());
		}
	}

	public static final class SplitImpl extends ProcessedNamedMinecraftProvider<SplitMinecraftProvider, NamedMinecraftProvider.SplitImpl> implements Split {
		public SplitImpl(NamedMinecraftProvider.SplitImpl parentMinecraftProvide, MinecraftJarProcessorManager jarProcessorManager) {
			super(parentMinecraftProvide, jarProcessorManager);
		}

		@Override
		public MinecraftJar getCommonJar() {
			return getProcessedPath(getParentMinecraftProvider().getCommonJar());
		}

		@Override
		public MinecraftJar getClientOnlyJar() {
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
		public MinecraftJar getEnvOnlyJar() {
			return getProcessedPath(getParentMinecraftProvider().getEnvOnlyJar());
		}

		@Override
		public SingleJarEnvType env() {
			return env;
		}
	}
}
