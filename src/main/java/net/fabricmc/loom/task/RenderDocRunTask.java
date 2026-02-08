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

import java.io.File;

import javax.inject.Inject;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;
import org.gradle.work.DisableCachingByDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.configuration.ide.RunConfigSettings;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.Platform;

@DisableCachingByDefault
public abstract class RenderDocRunTask extends RunGameTask {
	private static final Logger LOGGER = LoggerFactory.getLogger(RenderDocRunTask.class);

	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getRenderDocExecutable();

	@Input
	public abstract ListProperty<String> getRenderDocArgs();

	@Inject
	protected abstract ExecOperations getExecOperations();

	@Inject
	public RenderDocRunTask(RunConfigSettings settings) {
		super(settings);
		setGroup(Constants.TaskGroup.FABRIC);
		dependsOn("configureClientLaunch");
		getRenderDocArgs().addAll("capture", "--wait-for-exit");
	}

	@Override
	public void exec() {
		ExecResult result = getExecOperations().exec(exec -> {
			exec.workingDir(new File(getProjectDir().get(), getInternalRunDir().get()));
			exec.environment(getInternalEnvironmentVars().get());

			exec.commandLine(getRenderDocExecutable().get().getAsFile());
			exec.args(getRenderDocArgs().get());
			exec.args("--working-dir", new File(getProjectDir().get(), getInternalRunDir().get()));
			exec.args(getJavaLauncher().get().getExecutablePath());
			exec.args(getJvmArgs());
			exec.args("-D%s=true".formatted(Constants.Properties.RENDER_DOC));
			exec.args(getMainClass().get());

			for (CommandLineArgumentProvider provider : getArgumentProviders()) {
				exec.args(provider.asArguments());
			}

			LOGGER.info("Running command: {}", exec.getCommandLine());
		});
		result.assertNormalExitValue();
	}

	public static boolean isSupported(Platform platform) {
		final Platform.OperatingSystem os = platform.getOperatingSystem();
		final Platform.Architecture arch = platform.getArchitecture();
		// RenderDoc does support 32-bit Windows, but I cannot be bothered to test/maintain it
		return (os.isLinux() || os.isWindows()) && arch.isX64();
	}
}
