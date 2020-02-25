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

package net.fabricmc.loom.processors;

import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

import org.apache.commons.io.FileUtils;
import org.gradle.api.Project;

import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.providers.MappingsProvider;
import net.fabricmc.loom.providers.MinecraftMappedProvider;
import net.fabricmc.loom.providers.MinecraftProvider;
import net.fabricmc.loom.util.Constants;

public class MinecraftProcessedProvider extends MinecraftMappedProvider {
	public static final String PROJECT_MAPPED_CLASSIFIER = "projectmapped";

	private File projectMappedJar;

	private final JarProcessorManager jarProcessorManager;

	public MinecraftProcessedProvider(Project project, JarProcessorManager jarProcessorManager) {
		super(project);
		this.jarProcessorManager = jarProcessorManager;
	}

	@Override
	protected void addDependencies(DependencyInfo dependency, Consumer<Runnable> postPopulationScheduler) {
		if (jarProcessorManager.isInvalid(projectMappedJar)) {
			getProject().getLogger().lifecycle(":processing mapped jar");
			invalidateJars();

			try {
				FileUtils.copyFile(super.getMappedJar(), projectMappedJar);
			} catch (IOException e) {
				throw new RuntimeException("Failed to copy source jar", e);
			}

			jarProcessorManager.process(projectMappedJar);
		}

		getProject().getDependencies().add(Constants.MINECRAFT_NAMED,
				getProject().getDependencies().module("net.minecraft:minecraft:" + getJarVersionString(PROJECT_MAPPED_CLASSIFIER)));

		getProject().getDependencies().add(Constants.MINECRAFT_INTERMEDIARY,
				getProject().getDependencies().module("net.minecraft:minecraft:" + getJarVersionString("intermediary")));
	}

	private void invalidateJars() {
		if (projectMappedJar.exists()) {
			projectMappedJar.delete();
		}

		String[] byProducts = new String[]{"-linemapped.jar", "-sources.jar", "-sources.lmap"};

		for (String byProduct : byProducts) {
			File file = LoomGradlePlugin.getMappedByproduct(getProject(), byProduct);

			if (file.exists()) {
				file.delete();
			}
		}
	}

	@Override
	public void initFiles(MinecraftProvider minecraftProvider, MappingsProvider mappingsProvider) {
		super.initFiles(minecraftProvider, mappingsProvider);
		projectMappedJar = new File(getJarDirectory(getExtension().getProjectJarCache(), PROJECT_MAPPED_CLASSIFIER), "minecraft-" + getJarVersionString(PROJECT_MAPPED_CLASSIFIER) + ".jar");
	}

	@Override
	public File getMappedJar() {
		return projectMappedJar;
	}
}
