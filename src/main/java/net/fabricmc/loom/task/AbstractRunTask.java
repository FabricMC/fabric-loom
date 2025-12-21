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

import javax.inject.Inject;

import org.gradle.api.Action;
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
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.process.ExecOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.ide.RunConfig;
import net.fabricmc.loom.task.prod.TracyCapture;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.Platform;

public abstract class AbstractRunTask extends JavaExec {
	private static final Logger LOGGER = LoggerFactory.getLogger(AbstractRunTask.class);
	private static final String XVFB_PATH = "/usr/bin/xvfb-run";

	@Inject
	protected abstract ExecOperations getExecOperations();

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
	@Input
	protected abstract Property<Boolean> getUseXvfb();

	@Nested
	@Optional
	public abstract Property<TracyCapture> getTracyCapture();

	/**
	 * Configures the tracy profiler to run alongside the game. See @{@link TracyCapture} for more information.
	 *
	 * @param action The configuration action.
	 */
	@SuppressWarnings("unused")
	public void tracy(Action<? super TracyCapture> action) {
		getTracyCapture().set(getProject().getObjects().newInstance(TracyCapture.class));
		getTracyCapture().finalizeValue();
		action.execute(getTracyCapture().get());
	}

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

		getArgumentProviders().add(new CommandLineArgumentProvider() {
			@Override
			public Iterable<String> asArguments() {
				return config.get().programArgs;
			}
		});
		getArgumentProviders().add(new CommandLineArgumentProvider() {
			@Override
			public Iterable<String> asArguments() {
				if (AbstractRunTask.this.getTracyCapture().isPresent()) {
					return List.of("--tracy");
				}

				return List.of();
			}
		});
		getMainClass().set(config.map(runConfig -> runConfig.mainClass));
		getJvmArguments().addAll(getProject().provider(this::getGameJvmArgs));

		getInternalRunDir().set(config.map(runConfig -> runConfig.runDir));
		getInternalEnvironmentVars().set(config.map(runConfig -> runConfig.environmentVariables));
		getInternalJvmArgs().set(config.map(runConfig -> runConfig.vmArgs));
		getUseArgFile().set(getProject().provider(this::canUseArgFile));
		getProjectDir().set(getProject().getProjectDir().getAbsolutePath());

		// Set up useXvfb: convention is CI + Linux + client run config + xvfb exists
		getUseXvfb().convention(
				getProject().getProviders().environmentVariable("CI")
						.map(value -> Platform.CURRENT.getOperatingSystem().isLinux())
						.zip(config, (enabled, runConfig) -> enabled && runConfig.environment.equals("client"))
						.map(enabled -> enabled && Files.exists(Path.of(XVFB_PATH)))
						.orElse(false)
		);

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
		CharsetEncoder asciiEncoder = StandardCharsets.US_ASCII.newEncoder();

		return asciiEncoder.canEncode(getProject().getProjectDir().getAbsolutePath())
				&& asciiEncoder.canEncode(getProject().getGradle().getGradleUserHomeDir().getAbsolutePath());
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

		// Wrap with Tracy if enabled
		if (getTracyCapture().isPresent()) {
			try {
				getTracyCapture().get().runWithTracy(this::execInternal);
			} catch (IOException e) {
				throw new UncheckedIOException("Failed to run with Tracy", e);
			}

			return;
		}

		execInternal();
	}

	private void execInternal() {
		// Wrap with XVFB if enabled and on Linux
		if (getUseXvfb().get()) {
			LOGGER.info("Using XVFB for headless client execution");
			execWithXvfb();
		} else {
			super.exec();
		}
	}

	private void execWithXvfb() {
		String javaExec = getJavaLauncher().get().getExecutablePath().getAsFile().getAbsolutePath();

		// Build the complete command line: xvfb-run --auto-servernum java [jvm-args] mainclass [program-args]
		List<String> commandLine = new ArrayList<>();
		commandLine.add(XVFB_PATH);
		commandLine.add("--auto-servernum");
		commandLine.add(javaExec);
		commandLine.addAll(getJvmArguments().get());
		commandLine.add(getMainClass().get());
		commandLine.addAll(getArgs());

		for (CommandLineArgumentProvider provider : getArgumentProviders()) {
			for (String arg : provider.asArguments()) {
				commandLine.add(arg);
			}
		}

		getExecOperations().exec(execSpec -> {
			execSpec.setCommandLine(commandLine);
			execSpec.setWorkingDir(getWorkingDir());
			execSpec.setEnvironment(getEnvironment());
		});
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
	public static boolean containsAnyChar(final String value, final String chars) {
		return chars.length() > value.length()
				? containsAnyChar(value, chars, 0, value.length())
				: containsAnyChar(chars, value, 0, chars.length());
	}

	public static boolean containsAnyChar(final String value, final String chars, final int start, final int end) {
		for (int i = start; i < end; i++) {
			if (chars.indexOf(value.charAt(i)) >= 0) {
				return true;
			}
		}

		return false;
	}

	@Override
	public JavaExec setClasspath(FileCollection classpath) {
		this.getInternalClasspath().setFrom(classpath);
		return this;
	}

	@Override
	public JavaExec classpath(Object... paths) {
		this.getInternalClasspath().from(paths);
		return this;
	}

	@Override
	public FileCollection getClasspath() {
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
