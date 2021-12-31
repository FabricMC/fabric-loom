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

package net.fabricmc.loom.configuration.decompile;

import java.io.File;

import org.gradle.api.Project;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.providers.mappings.MappingsProviderImpl;
import net.fabricmc.loom.configuration.providers.minecraft.mapped.MergedMappedMinecraftProvider;
import net.fabricmc.loom.task.GenerateSourcesTask;
import net.fabricmc.loom.task.UnpickJarTask;
import net.fabricmc.loom.util.Constants;

public final class MergedDecompileConfiguration {
	private final Project project;
	private final MergedMappedMinecraftProvider minecraftProvider;
	private final LoomGradleExtension extension;
	private final MappingsProviderImpl mappingsProvider;

	public MergedDecompileConfiguration(Project project, MergedMappedMinecraftProvider minecraftProvider) {
		this.project = project;
		this.minecraftProvider = minecraftProvider;
		this.extension = LoomGradleExtension.get(project);
		this.mappingsProvider = extension.getMappingsProvider();
	}

	public void afterEvaluation() {
		File mappedJar = minecraftProvider.getMergedJar().toFile();

		if (mappingsProvider.hasUnpickDefinitions()) {
			File outputJar = new File(extension.getMappingsProvider().mappingsWorkingDir().toFile(), "minecraft-unpicked.jar");

			project.getTasks().register("unpickJar", UnpickJarTask.class, unpickJarTask -> {
				unpickJarTask.getUnpickDefinitions().set(mappingsProvider.getUnpickDefinitionsFile());
				unpickJarTask.getInputJar().set(minecraftProvider.getMergedJar().toFile());
				unpickJarTask.getOutputJar().set(outputJar);
			});

			mappedJar = outputJar;
		}

		final File inputJar = mappedJar;

		LoomGradleExtension.get(project).getGameDecompilers().forEach(decompiler -> {
			String taskName = "genSourcesWith" + decompiler.name();
			// Decompiler will be passed to the constructor of GenerateSourcesTask
			project.getTasks().register(taskName, GenerateSourcesTask.class, decompiler).configure(task -> {
				task.getInputJar().set(inputJar);
				task.dependsOn(project.getTasks().named("validateAccessWidener"));
				task.setDescription("Decompile minecraft using %s.".formatted(decompiler.name()));
				task.setGroup(Constants.TaskGroup.FABRIC);

				if (mappingsProvider.hasUnpickDefinitions()) {
					task.dependsOn(project.getTasks().named("unpickJar"));
				}
			});
		});

		project.getTasks().register("genSources", task -> {
			task.setDescription("Decompile minecraft using the default decompiler.");
			task.setGroup(Constants.TaskGroup.FABRIC);

			task.dependsOn(project.getTasks().named("genSourcesWithCfr"));
		});
	}
}
