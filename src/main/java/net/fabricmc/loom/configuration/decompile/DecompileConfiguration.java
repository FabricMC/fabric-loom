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

import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.ConfigContext;
import net.fabricmc.loom.configuration.providers.mappings.MappingConfiguration;
import net.fabricmc.loom.configuration.providers.minecraft.mapped.MappedMinecraftProvider;
import net.fabricmc.loom.task.UnpickJarTask;

public abstract class DecompileConfiguration<T extends MappedMinecraftProvider> {
	protected final Project project;
	protected final T minecraftProvider;
	protected final LoomGradleExtension extension;
	protected final MappingConfiguration mappingConfiguration;

	public DecompileConfiguration(ConfigContext configContext, T minecraftProvider) {
		this.project = configContext.project();
		this.minecraftProvider = minecraftProvider;
		this.extension = configContext.extension();
		this.mappingConfiguration = extension.getMappingConfiguration();
	}

	public abstract void afterEvaluation();

	protected final TaskProvider<UnpickJarTask> createUnpickJarTask(String name, File inputJar, File outputJar) {
		return project.getTasks().register(name, UnpickJarTask.class, unpickJarTask -> {
			unpickJarTask.getUnpickDefinitions().set(mappingConfiguration.getUnpickDefinitionsFile());
			unpickJarTask.getInputJar().set(inputJar);
			unpickJarTask.getOutputJar().set(outputJar);
		});
	}
}
