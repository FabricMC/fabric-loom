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

package net.fabricmc.loom.configuration.decompile;

import java.io.File;
import java.util.List;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.ConfigContext;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftJar;
import net.fabricmc.loom.configuration.providers.minecraft.mapped.MappedMinecraftProvider;
import net.fabricmc.loom.task.GenerateSourcesTask;
import net.fabricmc.loom.util.Constants;

public class SingleJarDecompileConfiguration extends DecompileConfiguration<MappedMinecraftProvider> {
	public SingleJarDecompileConfiguration(ConfigContext configContext, MappedMinecraftProvider minecraftProvider) {
		super(configContext, minecraftProvider);
	}

	@Override
	public final void afterEvaluation() {
		final List<MinecraftJar> minecraftJars = minecraftProvider.getMinecraftJars();
		assert minecraftJars.size() == 1;
		final MinecraftJar minecraftJar = minecraftJars.get(0);

		LoomGradleExtension.get(project).getDecompilerOptions().forEach(options -> {
			final String decompilerName = options.getFormattedName();
			String taskName = "genSourcesWith" + decompilerName;
			// Decompiler will be passed to the constructor of GenerateSourcesTask
			project.getTasks().register(taskName, GenerateSourcesTask.class, options).configure(task -> {
				task.getInputJarName().set(minecraftJar.getName());
				task.getOutputJar().fileValue(GenerateSourcesTask.getMappedJarFileWithSuffix("-sources.jar", minecraftJar.getPath()));

				task.dependsOn(project.getTasks().named("validateAccessWidener"));
				task.setDescription("Decompile minecraft using %s.".formatted(decompilerName));
				task.setGroup(Constants.TaskGroup.FABRIC);

				if (mappingConfiguration.hasUnpickDefinitions()) {
					final File outputJar = new File(extension.getMappingConfiguration().mappingsWorkingDir().toFile(), "minecraft-unpicked.jar");
					configureUnpick(task, outputJar);
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
