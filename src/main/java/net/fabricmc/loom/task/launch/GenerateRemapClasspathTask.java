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

package net.fabricmc.loom.task.launch;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.gradle.api.tasks.TaskAction;

import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.RemappedConfigurationEntry;
import net.fabricmc.loom.task.AbstractLoomTask;
import net.fabricmc.loom.util.Constants;

public abstract class GenerateRemapClasspathTask extends AbstractLoomTask {
	@TaskAction
	public void run() {
		List<String> inputConfigurations = new ArrayList<>();
		inputConfigurations.add(Constants.Configurations.LOADER_DEPENDENCIES);
		inputConfigurations.addAll(Constants.MOD_COMPILE_ENTRIES.stream().map(RemappedConfigurationEntry::sourceConfiguration).toList());

		List<File> remapClasspath = new ArrayList<>();

		for (String inputConfiguration : inputConfigurations) {
			remapClasspath.addAll(getProject().getConfigurations().getByName(inputConfiguration).getFiles());
		}

		for (Path minecraftJar : getExtension().getMinecraftJars(MappingsNamespace.INTERMEDIARY)) {
			remapClasspath.add(minecraftJar.toFile());
		}

		String str = remapClasspath.stream()
				.map(File::getAbsolutePath)
				.collect(Collectors.joining(File.pathSeparator));

		try {
			Files.writeString(getExtension().getFiles().getRemapClasspathFile().toPath(), str);
		} catch (IOException e) {
			throw new RuntimeException("Failed to generate remap classpath", e);
		}
	}
}
