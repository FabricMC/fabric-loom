/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016, 2017, 2018 FabricMC
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

package net.fabricmc.loom.configuration.processors;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Consumer;

import org.apache.commons.io.FileUtils;
import org.gradle.api.Project;

import net.fabricmc.loom.configuration.providers.MinecraftProvider;
import net.fabricmc.loom.configuration.providers.mappings.MappingsProvider;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftMappedProvider;
import net.fabricmc.loom.util.Constants;

public class MinecraftProcessedProvider extends MinecraftMappedProvider {
	public static final String PROJECT_OBF_CLASSIFIER = "projectobf";
	public static final String PROJECT_INT_CLASSIFIER = "projectintermediary";
	public static final String PROJECT_MAPPED_CLASSIFIER = "projectmapped";

	private File projectObfJar;
	private File projectIntJar;
	private File projectMappedJar;
	private boolean intInvalidated = false;
	private boolean mappedInvalidated = false;
	private final JarProcessorManager jarProcessorManager;

	public MinecraftProcessedProvider(Project project, JarProcessorManager jarProcessorManager) {
		super(project);
		this.jarProcessorManager = jarProcessorManager;
	}

	@Override
	public void provide(DependencyInfo dependency, Consumer<Runnable> postPopulationScheduler) throws Exception {
		if (jarProcessorManager.hasStage(JarProcessor.Stage.OBF) &&
				(jarProcessorManager.isInvalid(projectObfJar, JarProcessor.Stage.OBF) || isRefreshDeps())) {
			System.out.println(jarProcessorManager.isInvalid(projectObfJar, JarProcessor.Stage.OBF));
			getProject().getLogger().lifecycle(":processing obf jar");
			invalidateJars(JarProcessor.Stage.OBF);
			intInvalidated = true;

			try {
				FileUtils.copyFile(minecraftProvider.getMergedJar(), projectObfJar);
			} catch (IOException e) {
				throw new UncheckedIOException("Failed to copy source jar", e);
			}

			jarProcessorManager.process(projectObfJar, JarProcessor.Stage.OBF);
		}

		if ((jarProcessorManager.hasStage(JarProcessor.Stage.INTERMEDIARY) || jarProcessorManager.hasStage(JarProcessor.Stage.OBF)) &&
				(jarProcessorManager.isInvalid(projectIntJar, JarProcessor.Stage.INTERMEDIARY) || intInvalidated)) {
			getProject().getLogger().lifecycle(":processing intermediary jar");
			invalidateJars(JarProcessor.Stage.INTERMEDIARY);
			intInvalidated = false;
			mappedInvalidated = true;

			try {
				if (projectObfJar.exists()) {
					this.mapMinecraftJar(projectObfJar.toPath(), projectIntJar.toPath(), "official", "intermediary");
				} else {
					FileUtils.copyFile(super.getIntermediaryJar(), projectIntJar);
				}
			} catch (IOException e) {
				throw new UncheckedIOException("Failed to copy source jar", e);
			}

			jarProcessorManager.process(projectIntJar, JarProcessor.Stage.INTERMEDIARY);
		}

		if (!mappedInvalidated) {
			// if mapped isn't invalidated, then that means we're still using the normal jars, so we delegate to normal MinecraftMappedProvider
			super.provide(dependency, postPopulationScheduler);
		} else {
			addDependencies(dependency, postPopulationScheduler);
		}
	}


	@Override
	protected void addDependencies(DependencyInfo dependency, Consumer<Runnable> postPopulationScheduler) {
		if (jarProcessorManager.isInvalid(projectMappedJar, JarProcessor.Stage.MAPPED) || mappedInvalidated) {
			getProject().getLogger().lifecycle(":processing mapped jar");
			invalidateJars(JarProcessor.Stage.MAPPED);
			mappedInvalidated = false;

			try {
				if (projectIntJar.exists()) {
					this.mapMinecraftJar(projectIntJar.toPath(), projectMappedJar.toPath(), "intermediary", "named");
				} else {
					FileUtils.copyFile(super.getMappedJar(), projectMappedJar);
				}
			} catch (IOException e) {
				throw new RuntimeException("Failed to copy source jar", e);
			}

			jarProcessorManager.process(projectMappedJar, JarProcessor.Stage.MAPPED);
		}

		getProject().getRepositories().flatDir(repository -> repository.dir(getJarDirectory(getExtension().getProjectPersistentCache(), PROJECT_MAPPED_CLASSIFIER)));

		getProject().getDependencies().add(Constants.Configurations.MINECRAFT_NAMED,
				getProject().getDependencies().module("net.minecraft:minecraft:" + getJarVersionString(PROJECT_MAPPED_CLASSIFIER)));
	}

	private void invalidateJars(JarProcessor.Stage stage) {
		File obfDir = getJarDirectory(getExtension().getUserCache(), PROJECT_OBF_CLASSIFIER);
		File intDir = getJarDirectory(getExtension().getUserCache(), PROJECT_INT_CLASSIFIER);
		File mappedDir = getJarDirectory(getExtension().getUserCache(), PROJECT_MAPPED_CLASSIFIER);
		getProject().getLogger().warn("Invalidating project jars");

		try {
			if (stage == JarProcessor.Stage.OBF) {
				cleanDirectory(obfDir);
				cleanDirectory(intDir);
				cleanDirectory(mappedDir);
			} else if (stage == JarProcessor.Stage.INTERMEDIARY) {
				cleanDirectory(intDir);
				cleanDirectory(mappedDir);
			} else {
				cleanDirectory(mappedDir);
			}
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to invalidate jars, try stopping gradle daemon or closing the game", e);
		}
	}

	private void cleanDirectory(File file) throws IOException {
		if (file.exists()) {
			FileUtils.cleanDirectory(projectObfJar);
		}
	}

	@Override
	public void initFiles(MinecraftProvider minecraftProvider, MappingsProvider mappingsProvider) {
		super.initFiles(minecraftProvider, mappingsProvider);
		projectObfJar = new File(getObfJarDirectory(PROJECT_OBF_CLASSIFIER), "minecraft-" + minecraftProvider.getMinecraftVersion() + "-projectobf" + ".jar");
		projectIntJar = new File(getJarDirectory(getExtension().getProjectPersistentCache(), PROJECT_INT_CLASSIFIER),
				"minecraft-" + getJarVersionString(PROJECT_INT_CLASSIFIER) + ".jar");
		projectMappedJar = new File(getJarDirectory(getExtension().getProjectPersistentCache(), PROJECT_MAPPED_CLASSIFIER),
				"minecraft-" + getJarVersionString(PROJECT_MAPPED_CLASSIFIER) + ".jar");
	}

	private File getObfJarDirectory(String classifier) {
		return new File(getExtension().getProjectPersistentCache(), minecraftProvider.getMinecraftVersion() + "-" + classifier);
	}

	@Override
	public File getMappedJar() {
		return projectMappedJar;
	}
}
