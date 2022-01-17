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
import java.nio.file.Path;
import java.util.List;

import org.gradle.api.Project;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.providers.minecraft.mapped.MappedMinecraftProvider;
import net.fabricmc.loom.task.GenerateSourcesTask;
import net.fabricmc.loom.util.Constants;

public class SingleJarDecompileConfiguration extends DecompileConfiguration<MappedMinecraftProvider> {
	public SingleJarDecompileConfiguration(Project project, MappedMinecraftProvider minecraftProvider) {
		super(project, minecraftProvider);
	}

	@Override
	public final void afterEvaluation() {
		List<Path> minecraftJars = minecraftProvider.getMinecraftJars();
		assert minecraftJars.size() == 1;

		final File namedJar = minecraftJars.get(0).toFile();

		File mappedJar = namedJar;

		if (mappingsProvider.hasUnpickDefinitions()) {
			File outputJar = new File(extension.getMappingsProvider().mappingsWorkingDir().toFile(), "minecraft-unpicked.jar");
			createUnpickJarTask("unpickJar", namedJar, outputJar);

			mappedJar = outputJar;
		}

		final File inputJar = mappedJar;

		LoomGradleExtension.get(project).getDecompilerOptions().forEach(options -> {
			final String decompilerName = options.getName().substring(0, 1).toUpperCase() + options.getName().substring(1);
			String taskName = "genSourcesWith" + decompilerName;
			// Decompiler will be passed to the constructor of GenerateSourcesTask
			project.getTasks().register(taskName, GenerateSourcesTask.class, options).configure(task -> {
				task.getInputJar().set(inputJar);
				task.getRuntimeJar().set(namedJar);

				task.dependsOn(project.getTasks().named("validateAccessWidener"));
				task.setDescription("Decompile minecraft using %s.".formatted(decompilerName));
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
