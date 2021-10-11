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

package net.fabricmc.loom.task;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import javax.inject.Inject;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.OutputFile;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.providers.LaunchProvider;
import net.fabricmc.loom.extension.LoomFiles;
import net.fabricmc.loom.util.Constants;

public abstract class UnpickJarTask extends JavaExec {
	@InputFile
	public abstract RegularFileProperty getInputJar();

	@InputFile
	public abstract RegularFileProperty getUnpickDefinitions();

	@InputFiles
	// Only 1 file, but it comes from a configuration
	public abstract ConfigurableFileCollection getConstantJar();

	@InputFiles
	public abstract ConfigurableFileCollection getUnpickClasspath();

	@OutputFile
	public abstract RegularFileProperty getOutputJar();

	@Inject
	public UnpickJarTask() {
		classpath(getProject().getConfigurations().getByName(Constants.Configurations.UNPICK_CLASSPATH));
		getMainClass().set("daomephsta.unpick.cli.Main");

		getConstantJar().setFrom(getProject().getConfigurations().getByName(Constants.Configurations.MAPPING_CONSTANTS));
		getUnpickClasspath().setFrom(getProject().getConfigurations().getByName(Constants.Configurations.MINECRAFT_DEPENDENCIES));
	}

	@Override
	public void exec() {
		fileArg(getInputJar().get().getAsFile(), getOutputJar().get().getAsFile(), getUnpickDefinitions().get().getAsFile());
		fileArg(getConstantJar().getSingleFile());

		// Classpath
		fileArg(getExtension().getMinecraftMappedProvider().getMappedJar());

		for (File file : getUnpickClasspath()) {
			fileArg(file);
		}

		writeUnpickLogConfig();
		systemProperty("java.util.logging.config.file", getDirectories().getUnpickLoggingConfigFile().getAbsolutePath());

		super.exec();
	}

	private void writeUnpickLogConfig() {
		try (InputStream is = LaunchProvider.class.getClassLoader().getResourceAsStream("unpick-logging.properties")) {
			Files.deleteIfExists(getDirectories().getUnpickLoggingConfigFile().toPath());
			Files.copy(is, getDirectories().getUnpickLoggingConfigFile().toPath());
		} catch (IOException e) {
			throw new RuntimeException("Failed to copy unpick logging config", e);
		}
	}

	private void fileArg(File... files) {
		for (File file : files) {
			args(file.getAbsolutePath());
		}
	}

	@Internal
	protected LoomGradleExtension getExtension() {
		return LoomGradleExtension.get(getProject());
	}

	private LoomFiles getDirectories() {
		return getExtension().getFiles();
	}
}
