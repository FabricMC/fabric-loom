/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2018-2021 FabricMC
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
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.JavaExec;
import org.jetbrains.annotations.NotNull;

import net.fabricmc.loom.configuration.ide.RunConfig;
import net.fabricmc.loom.util.Constants;

public abstract class AbstractRunTask extends JavaExec {
	private final RunConfig config;
	// We control the classpath, as we use a ArgFile to pass it over the command line: https://docs.oracle.com/javase/7/docs/technotes/tools/windows/javac.html#commandlineargfile
	private final ConfigurableFileCollection classpath = getProject().getObjects().fileCollection();

	public AbstractRunTask(Function<Project, RunConfig> configProvider) {
		super();
		setGroup(Constants.TaskGroup.FABRIC);
		this.config = configProvider.apply(getProject());

		setClasspath(config.sourceSet.getRuntimeClasspath().filter(File::exists).filter(new LibraryFilter()));

		args(config.programArgs);
		getMainClass().set(config.mainClass);
	}

	private boolean canUseArgFile() {
		// @-files were added for java (not javac) in Java 9, see https://bugs.openjdk.org/browse/JDK-8027634
		return getJavaVersion().isJava9Compatible();
	}

	@Override
	public void exec() {
		if (canUseArgFile()) {
			getProject().getLogger().debug("Using arg file for {}", getName());
			// We're using an arg file, pass an empty classpath to the super JavaExec.
			super.setClasspath(getProject().files());
		} else {
			getProject().getLogger().debug("Using bare classpath for {}", getName());
			// The classpath is passed normally, so pass the full classpath to the super JavaExec.
			super.setClasspath(classpath);
		}

		setWorkingDir(new File(getProject().getProjectDir(), config.runDir));
		environment(config.environmentVariables);

		super.exec();
	}

	@Override
	public void setWorkingDir(File dir) {
		if (!dir.exists()) {
			dir.mkdirs();
		}

		super.setWorkingDir(dir);
	}

	@Override
	public List<String> getJvmArgs() {
		final List<String> superArgs = super.getJvmArgs();
		final List<String> args = new ArrayList<>();

		if (canUseArgFile()) {
			final String content = "-classpath\n" + this.classpath.getFiles().stream()
					.map(File::getAbsolutePath)
					.collect(Collectors.joining(System.getProperty("path.separator")));

			try {
				final Path argsFile = Files.createTempFile("loom-classpath", ".args");
				Files.writeString(argsFile, content, StandardCharsets.UTF_8);
				args.add("@" + argsFile.toAbsolutePath());
			} catch (IOException e) {
				throw new UncheckedIOException("Failed to create classpath file", e);
			}
		}

		if (superArgs != null) {
			args.addAll(superArgs);
		}

		args.addAll(config.vmArgs);
		return args;
	}

	@Override
	public @NotNull JavaExec setClasspath(@NotNull FileCollection classpath) {
		this.classpath.setFrom(classpath);
		return this;
	}

	@Override
	public @NotNull JavaExec classpath(Object @NotNull... paths) {
		this.classpath.from(paths);
		return this;
	}

	@Override
	public @NotNull FileCollection getClasspath() {
		return this.classpath;
	}

	private class LibraryFilter implements Spec<File> {
		private List<String> excludedLibraryPaths = null;

		@Override
		public boolean isSatisfiedBy(File element) {
			if (excludedLibraryPaths == null) {
				excludedLibraryPaths = config.getExcludedLibraryPaths(getProject());
			}

			if (excludedLibraryPaths.contains(element.getAbsolutePath())) {
				getProject().getLogger().debug("Excluding library {} from {} run config", element.getName(), config.configName);
				return false;
			}

			return true;
		}
	}
}
