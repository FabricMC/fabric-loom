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

package net.fabricmc.loom.task.launch;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import net.fabricmc.loom.api.RemapConfigurationSettings;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.task.AbstractLoomTask;
import net.fabricmc.loom.util.Constants;

public abstract class GenerateRemapClasspathTask extends AbstractLoomTask {
	@InputFiles
	public abstract ConfigurableFileCollection getRemapClasspath();

	@OutputFile
	public abstract RegularFileProperty getRemapClasspathFile();

	public GenerateRemapClasspathTask() {
		final ConfigurationContainer configurations = getProject().getConfigurations();

		getRemapClasspath().from(configurations.named(Constants.Configurations.MINECRAFT_COMPILE_LIBRARIES));
		getExtension().getRuntimeRemapConfigurations().stream()
				.map(RemapConfigurationSettings::getName)
				.map(configurations::named)
				.forEach(getRemapClasspath()::from);

		for (Path minecraftJar : getExtension().getMinecraftJars(MappingsNamespace.INTERMEDIARY)) {
			getRemapClasspath().from(minecraftJar.toFile());
		}

		getRemapClasspathFile().set(getExtension().getFiles().getRemapClasspathFile());
	}

	@TaskAction
	public void run() {
		final List<File> remapClasspath = new ArrayList<>(getRemapClasspath().getFiles());

		String str = remapClasspath.stream()
				.map(File::getAbsolutePath)
				.collect(Collectors.joining(File.pathSeparator));

		try {
			Files.writeString(getRemapClasspathFile().getAsFile().get().toPath(), str);
		} catch (IOException e) {
			throw new RuntimeException("Failed to generate remap classpath", e);
		}
	}
}
