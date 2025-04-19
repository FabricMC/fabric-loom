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

import java.util.List;
import java.util.stream.Stream;

import com.google.common.collect.Streams;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Exec;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.toolchain.JavaLauncher;

import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.Platform;

public abstract class RenderDocRunTask extends Exec {
	@InputFile
	public abstract RegularFileProperty getRenderDocExecutable();

	@Input
	public abstract ListProperty<String> getRenderDocArgs();

	public RenderDocRunTask(TaskProvider<RunGameTask> runGameTask) {
		setGroup(Constants.TaskGroup.RENDERDOC);

		getInputs().files(runGameTask.map(RunGameTask::getClasspath));

		getRenderDocArgs().add("--wait-for-exit");

		args(getRenderDocExecutable());
		args("capture");
		args(getRenderDocArgs());
		args("--working-dir",
				runGameTask.map(RunGameTask::getWorkingDir),
				runGameTask.flatMap(RunGameTask::getJavaLauncher).map(JavaLauncher::getExecutablePath)
		);
		args(runGameTask.map(RunGameTask::getJvmArguments));
		args(runGameTask.map(RunGameTask::getMainClass));
		args(runGameTask.map(RunGameTask::getArgumentProviders)
				.map(List::stream)
				.map(providers -> providers.flatMap(provider -> Streams.stream(provider.asArguments())))
				.map(Stream::toList)
		);

		// No way to get or set this lazily?
		environment(runGameTask.get().getEnvironment());
	}

	public static boolean isSupported(Platform platform) {
		final Platform.OperatingSystem os = platform.getOperatingSystem();
		final Platform.Architecture arch = platform.getArchitecture();
		// RenderDoc does support 32-bit Windows, but I cannot be bothered to test/maintain it
		return (os.isLinux() || os.isWindows()) && arch.isX64();
	}
}
