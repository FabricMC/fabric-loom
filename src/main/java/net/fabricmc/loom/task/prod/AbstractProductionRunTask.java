/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 FabricMC
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

package net.fabricmc.loom.task.prod;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;
import org.gradle.process.ExecSpec;
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.configuration.InstallerData;
import net.fabricmc.loom.task.AbstractLoomTask;
import net.fabricmc.loom.task.RemapTaskConfiguration;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.gradle.GradleUtils;

/**
 * This is the base task for running the game in a "production" like environment. Using intermediary names, and not enabling development only features.
 *
 * <p>Do not use this task directly, use {@link ClientProductionRunTask} or {@link ServerProductionRunTask} instead.
 */
@ApiStatus.Experimental
public abstract sealed class AbstractProductionRunTask extends AbstractLoomTask permits ClientProductionRunTask, ServerProductionRunTask {
	private static final Logger LOGGER = LoggerFactory.getLogger(AbstractProductionRunTask.class);

	/**
	 * A collection of mods that will be used when running the game. The mods must be remapped to run with intermediary names.
	 *
	 * <p>By default this includes the remapped jar.
	 */
	@Classpath
	public abstract ConfigurableFileCollection getMods();

	/**
	 * A list of additional JVM arguments to pass to the game.
	 */
	@Input
	public abstract ListProperty<String> getJvmArgs();

	/**
	 * A list of additional program arguments to pass to the game.
	 */
	@Input
	public abstract ListProperty<String> getProgramArgs();

	/**
	 * The directory to run the game in.
	 */
	@OutputDirectory
	public abstract DirectoryProperty getRunDir();

	/**
	 * The {@link JavaLauncher} to use when running the game, this can be used to specify a specific Java version to use.
	 *
	 * <p>See: <a href="https://docs.gradle.org/current/userguide/toolchains.html#sec:plugins_toolchains">Java Toolchains</a>
	 * @return
	 */
	@Nested
	public abstract Property<JavaLauncher> getJavaLauncher();

	// Internal options
	@ApiStatus.Internal
	@Classpath
	protected abstract ConfigurableFileCollection getClasspath();

	@ApiStatus.Internal
	@Input
	protected abstract Property<String> getMainClass();

	@Inject
	protected abstract ExecOperations getExecOperations();

	@Inject
	protected abstract JavaToolchainService getJavaToolchainService();

	@Inject
	public AbstractProductionRunTask() {
		JavaToolchainSpec defaultToolchain = getProject().getExtensions().getByType(JavaPluginExtension.class).getToolchain();
		getJavaLauncher().convention(getJavaToolchainService().launcherFor(defaultToolchain));
		getRunDir().convention(getProject().getLayout().getProjectDirectory().dir("run"));

		if (!GradleUtils.getBooleanProperty(getProject(), Constants.Properties.DONT_REMAP)) {
			getMods().from(getProject().getTasks().named(RemapTaskConfiguration.REMAP_JAR_TASK_NAME));
		}
	}

	@TaskAction
	public void run() throws IOException {
		Files.createDirectories(getRunDir().get().getAsFile().toPath());

		ExecResult result = getExecOperations().exec(exec -> {
			configureCommand(exec);
			configureJvmArgs(exec);
			configureClasspath(exec);
			configureMainClass(exec);
			configureProgramArgs(exec);

			exec.setWorkingDir(getRunDir());

			LOGGER.debug("Running command: {}", exec.getCommandLine());
		});
		result.assertNormalExitValue();
	}

	protected void configureCommand(ExecSpec exec) {
		exec.commandLine(getJavaLauncher().get().getExecutablePath());
	}

	protected void configureJvmArgs(ExecSpec exec) {
		exec.args(getJvmArgs().get());
		exec.args("-Dfabric.addMods=" + joinFiles(getMods().getFiles().stream()));
	}

	protected Stream<File> streamClasspath() {
		return getClasspath().getFiles().stream();
	}

	protected void configureClasspath(ExecSpec exec) {
		exec.args("-cp");
		exec.args(joinFiles(streamClasspath()));
	}

	protected void configureMainClass(ExecSpec exec) {
		exec.args(getMainClass().get());
	}

	protected void configureProgramArgs(ExecSpec exec) {
		exec.args(getProgramArgs().get());
	}

	@Internal
	protected Provider<String> getProjectLoaderVersion() {
		return getProject().provider(() -> {
			InstallerData installerData = getExtension().getInstallerData();

			if (installerData == null) {
				return null;
			}

			return installerData.version();
		});
	}

	protected Provider<Configuration> detachedConfigurationProvider(String mavenNotation, Provider<String> versionProvider) {
		return versionProvider.map(version -> {
			Dependency serverLauncher = getProject().getDependencies().create(mavenNotation.formatted(version));
			return getProject().getConfigurations().detachedConfiguration(serverLauncher);
		});
	}

	private static String joinFiles(Stream<File> stream) {
		return stream.map(File::getAbsolutePath)
				.collect(Collectors.joining(File.pathSeparator));
	}
}
