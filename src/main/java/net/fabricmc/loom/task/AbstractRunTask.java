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
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.JavaExec;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.ide.RunConfig;
import net.fabricmc.loom.util.Constants;

public abstract class AbstractRunTask extends JavaExec {
	private static final CharsetEncoder ASCII_ENCODER = StandardCharsets.US_ASCII.newEncoder();
	private static final Logger LOGGER = LoggerFactory.getLogger(AbstractRunTask.class);

	@Input
	protected abstract Property<String> getInternalRunDir();
	@Input
	protected abstract MapProperty<String, Object> getInternalEnvironmentVars();
	@Input
	protected abstract ListProperty<String> getInternalJvmArgs();
	@Input
	protected abstract Property<Boolean> getUseArgFile();
	@Input
	protected abstract Property<String> getProjectDir();
	@Input
	// We use a string here, as it's technically an output, but we don't want to cache runs of this task by default.
	protected abstract Property<String> getArgFilePath();

	// We control the classpath, as we use a ArgFile to pass it over the command line: https://docs.oracle.com/javase/7/docs/technotes/tools/windows/javac.html#commandlineargfile
	@InputFiles
	protected abstract ConfigurableFileCollection getInternalClasspath();

	public AbstractRunTask(Function<Project, RunConfig> configProvider) {
		super();
		setGroup(Constants.TaskGroup.FABRIC);

		final Provider<RunConfig> config = getProject().provider(() -> configProvider.apply(getProject()));

		getInternalClasspath().from(config.map(runConfig -> runConfig.sourceSet.getRuntimeClasspath()
				.filter(new LibraryFilter(
						config.get().getExcludedLibraryPaths(getProject()),
						config.get().configName)
				)));

		getArgumentProviders().add(() -> config.get().programArgs);
		getMainClass().set(config.map(runConfig -> runConfig.mainClass));
		getJvmArguments().addAll(getProject().provider(this::getGameJvmArgs));

		getInternalRunDir().set(config.map(runConfig -> runConfig.runDir));
		getInternalEnvironmentVars().set(config.map(runConfig -> runConfig.environmentVariables));
		getInternalJvmArgs().set(config.map(runConfig -> runConfig.vmArgs));
		getUseArgFile().set(getProject().provider(this::canUseArgFile));
		getProjectDir().set(getProject().getProjectDir().getAbsolutePath());

		File buildCache = LoomGradleExtension.get(getProject()).getFiles().getProjectBuildCache();
		File argFile = new File(buildCache, "argFiles/" + getName());
		getArgFilePath().set(argFile.getAbsolutePath());
	}

	private boolean canUseArgFile() {
		if (!canPathBeASCIIEncoded()) {
			// The gradle home or project dir contain chars that cannot be ascii encoded, thus are not supported by an arg file.
			return false;
		}

		// @-files were added for java (not javac) in Java 9, see https://bugs.openjdk.org/browse/JDK-8027634
		return getJavaVersion().isJava9Compatible();
	}

	private boolean canPathBeASCIIEncoded() {
		return ASCII_ENCODER.canEncode(getProject().getProjectDir().getAbsolutePath())
				&& ASCII_ENCODER.canEncode(getProject().getGradle().getGradleUserHomeDir().getAbsolutePath());
	}

	@Override
	public void exec() {
		if (getUseArgFile().get()) {
			LOGGER.debug("Using arg file for {}", getName());
			// We're using an arg file, pass an empty classpath to the super JavaExec.
			super.setClasspath(getObjectFactory().fileCollection());
		} else {
			LOGGER.debug("Using bare classpath for {}", getName());
			// The classpath is passed normally, so pass the full classpath to the super JavaExec.
			super.setClasspath(getInternalClasspath());
		}

		setWorkingDir(new File(getProjectDir().get(), getInternalRunDir().get()));
		environment(getInternalEnvironmentVars().get());

		super.exec();
	}

	@Override
	public void setWorkingDir(File dir) {
		if (!dir.exists()) {
			dir.mkdirs();
		}

		super.setWorkingDir(dir);
	}

	private List<String> getGameJvmArgs() {
		final List<String> args = new ArrayList<>();

		if (getUseArgFile().get()) {
			final String content = "-classpath\n" + this.getInternalClasspath().getFiles().stream()
					.map(File::getAbsolutePath)
					.map(AbstractRunTask::quoteArg)
					.collect(Collectors.joining(File.pathSeparator));

			try {
				final Path argsFile = Paths.get(getArgFilePath().get());
				Files.createDirectories(argsFile.getParent());
				Files.writeString(argsFile, content, StandardCharsets.UTF_8);
				args.add("@" + argsFile.toAbsolutePath());
			} catch (IOException e) {
				throw new UncheckedIOException("Failed to create classpath file", e);
			}
		}

		args.addAll(getInternalJvmArgs().get());
		return args;
	}

	// Based off https://github.com/JetBrains/intellij-community/blob/295dd68385a458bdfde638152e36d19bed18b666/platform/util/src/com/intellij/execution/CommandLineWrapperUtil.java#L87
	private static String quoteArg(String arg) {
		final String specials = " #'\"\n\r\t\f";

		if (!containsAnyChar(arg, specials)) {
			return arg;
		}

		final StringBuilder sb = new StringBuilder(arg.length() * 2);

		for (int i = 0; i < arg.length(); i++) {
			char c = arg.charAt(i);

			switch (c) {
			case ' ', '#', '\'' -> sb.append('"').append(c).append('"');
			case '"' -> sb.append("\"\\\"\"");
			case '\n' -> sb.append("\"\\n\"");
			case '\r' -> sb.append("\"\\r\"");
			case '\t' -> sb.append("\"\\t\"");
			case '\f' -> sb.append("\"\\f\"");
			default -> sb.append(c);
			}
		}

		return sb.toString();
	}

	// https://github.com/JetBrains/intellij-community/blob/295dd68385a458bdfde638152e36d19bed18b666/platform/util/base/src/com/intellij/openapi/util/text/Strings.java#L100-L118
	public static boolean containsAnyChar(final @NotNull String value, final @NotNull String chars) {
		return chars.length() > value.length()
				? containsAnyChar(value, chars, 0, value.length())
				: containsAnyChar(chars, value, 0, chars.length());
	}

	public static boolean containsAnyChar(final @NotNull String value, final @NotNull String chars, final int start, final int end) {
		for (int i = start; i < end; i++) {
			if (chars.indexOf(value.charAt(i)) >= 0) {
				return true;
			}
		}

		return false;
	}

	@Override
	public @NotNull JavaExec setClasspath(@NotNull FileCollection classpath) {
		this.getInternalClasspath().setFrom(classpath);
		return this;
	}

	@Override
	public @NotNull JavaExec classpath(Object @NotNull... paths) {
		this.getInternalClasspath().from(paths);
		return this;
	}

	@Override
	public @NotNull FileCollection getClasspath() {
		return this.getInternalClasspath();
	}

	public record LibraryFilter(List<String> excludedLibraryPaths, String configName) implements Spec<File> {
		@Override
		public boolean isSatisfiedBy(File element) {
			if (excludedLibraryPaths.contains(element.getAbsolutePath())) {
				LOGGER.debug("Excluding library {} from {} run config", element.getName(), configName);
				return false;
			}

			return true;
		}
	}
}
