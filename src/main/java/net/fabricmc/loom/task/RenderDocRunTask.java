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

package net.fabricmc.loom.task;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Nested;
import org.gradle.jvm.toolchain.JavaInstallationMetadata;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.jetbrains.annotations.NotNull;

import net.fabricmc.loom.configuration.ide.RunConfigSettings;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.Platform;

public abstract class RenderDocRunTask extends RunGameTask {
	@InputFile
	public abstract RegularFileProperty getRenderDocExecutable();

	@Input
	public abstract ListProperty<String> getRenderDocArgs();

	/**
	 * The {@link JavaLauncher} to use when running the game, this can be used to specify a specific Java version to use.
	 *
	 * <p>See: <a href="https://docs.gradle.org/current/userguide/toolchains.html#sec:plugins_toolchains">Java Toolchains</a>
	 * @return
	 */
	@Nested
	public abstract Property<JavaLauncher> getRenderDocJavaLauncher();

	@Inject
	protected abstract @NotNull JavaToolchainService getJavaToolchainService();

	@Inject
	public RenderDocRunTask(RunConfigSettings settings) {
		super(settings);
		setGroup(Constants.TaskGroup.FABRIC);
		dependsOn("configureClientLaunch");
		getRenderDocArgs().addAll("capture", "--wait-for-exit", "--working-dir", getWorkingDir().getAbsolutePath());

		JavaToolchainSpec defaultToolchain = getProject().getExtensions().getByType(JavaPluginExtension.class).getToolchain();
		getRenderDocJavaLauncher().convention(getJavaToolchainService().launcherFor(defaultToolchain));

		// Replace the default java launcher with one that runs the RenderDoc executable instead.
		super.getJavaLauncher().set(new JavaLauncher() {
			@Override
			public @NotNull JavaInstallationMetadata getMetadata() {
				return getRenderDocJavaLauncher().get().getMetadata();
			}

			@Override
			public @NotNull RegularFile getExecutablePath() {
				return getRenderDocExecutable().get();
			}
		});
	}

	@Override
	public void exec() {
		preExec();

		// Pre-append the RenderDoc arguments to the JVM arguments.
		List<String> args = new ArrayList<>(getRenderDocArgs().get());
		args.add(getRenderDocJavaLauncher().get().getExecutablePath().toString());
		args.addAll(getGameJvmArgs());
		getJvmArguments().set(args);

		super.exec();
	}

	/**
	 * @deprecated Use {@link #getRenderDocJavaLauncher()} instead.
	 */
	@Deprecated(forRemoval = true)
	@Override
	public @NotNull Property<JavaLauncher> getJavaLauncher() {
		return super.getJavaLauncher();
	}

	public static boolean isSupported(Platform platform) {
		final Platform.OperatingSystem os = platform.getOperatingSystem();
		final Platform.Architecture arch = platform.getArchitecture();
		// RenderDoc does support 32-bit Windows, but I cannot be bothered to test/maintain it
		return (os.isLinux() || os.isWindows()) && arch.isX64();
	}
}
