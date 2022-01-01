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

package net.fabricmc.loom.configuration.providers.minecraft.mapped.named;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.configuration.processors.JarProcessorManager;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftProvider;
import net.fabricmc.loom.util.Constants;

public abstract class ProcessedNamedMinecraftProvider<M extends MinecraftProvider, P extends NamedMinecraftProvider<M>> extends NamedMinecraftProvider<M> {
	private final P parentMinecraftProvider;
	private final JarProcessorManager jarProcessorManager;
	private final String projectMappedName;

	private Path projectMappedDir;
	private List<Path> parentJars;
	private List<Path> projectMappedJars;
	private Map<Path, Path> projectMappedJarMap;

	public ProcessedNamedMinecraftProvider(P parentMinecraftProvide, JarProcessorManager jarProcessorManager) {
		super(parentMinecraftProvide.getProject(), parentMinecraftProvide.getMinecraftProvider());
		this.parentMinecraftProvider = parentMinecraftProvide;
		this.jarProcessorManager = jarProcessorManager;

		this.projectMappedName = "minecraft-project-%s-".formatted(getProject().getPath().replace(':', '@'));
	}

	@Override
	public void setupFiles(Function<String, Path> function) {
		parentJars = parentMinecraftProvider.getMinecraftJars();

		final LoomGradleExtension extension = LoomGradleExtension.get(getProject());

		this.projectMappedDir = extension.getFiles().getRootProjectPersistentCache().toPath()
				.resolve(getMinecraftProvider().minecraftVersion())
				.resolve(extension.getMappingsProvider().mappingsIdentifier());

		projectMappedJars = new ArrayList<>();
		projectMappedJarMap = new HashMap<>();

		for (Path parentJar : parentJars) {
			Path projectMappedJar = projectMappedDir.resolve(parentJar.getFileName().toString().replace("minecraft-", projectMappedName));
			projectMappedJars.add(projectMappedJar);
			projectMappedJarMap.put(parentJar, projectMappedJar);
		}
	}

	@Override
	public void provide(boolean applyDependencies) throws Exception {
		parentMinecraftProvider.provide(false);

		setupFiles(null);

		boolean invalid = false;

		for (Map.Entry<Path, Path> entry : projectMappedJarMap.entrySet()) {
			if (jarProcessorManager.isInvalid(entry.getValue().toFile()) || LoomGradlePlugin.refreshDeps) {
				invalid = true;
				break;
			}
		}

		if (invalid) {
			try {
				Files.createDirectories(this.projectMappedDir);
			} catch (IOException e) {
				throw new UncheckedIOException("Failed to create project mapped dir", e);
			}

			for (Map.Entry<Path, Path> entry : projectMappedJarMap.entrySet()) {
				final Path inputJar = entry.getKey();
				final Path outputJar = entry.getValue();

				Files.copy(inputJar, outputJar, StandardCopyOption.REPLACE_EXISTING);
				jarProcessorManager.process(outputJar.toFile());
			}
		}

		if (applyDependencies) {
			applyDependencies();
		}
	}

	@Override
	protected void applyDependencies() {
		if (parentMinecraftProvider instanceof MergedNamedMinecraftProvider) {
			getProject().getDependencies().add(Constants.Configurations.MINECRAFT_NAMED, getDependencyNotation("merged"));
		} else if (parentMinecraftProvider instanceof SplitNamedMinecraftProvider) {
			// TODO sort out target configs!
			getProject().getDependencies().add(Constants.Configurations.MINECRAFT_NAMED, getDependencyNotation("common"));
			getProject().getDependencies().add(Constants.Configurations.MINECRAFT_NAMED, getDependencyNotation("clientonly"));
		} else {
			throw new UnsupportedOperationException();
		}
	}

	@Override
	protected String getName(String name) {
		return "%s%s-%s".formatted(projectMappedName, name, getTargetNamespace().toString());
	}

	@Override
	public List<RemappedJars> getRemappedJars() {
		throw new UnsupportedOperationException();
	}

	@Override
	public List<Path> getMinecraftJars() {
		return projectMappedJars;
	}

	public P getParentMinecraftProvider() {
		return parentMinecraftProvider;
	}

	public Path getParentPath(Function<P, Path> function) {
		return projectMappedJarMap.get(function.apply(getParentMinecraftProvider()));
	}
}
