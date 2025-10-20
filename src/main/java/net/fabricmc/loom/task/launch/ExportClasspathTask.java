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

package net.fabricmc.loom.task.launch;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import javax.inject.Inject;

import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;

import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.configuration.classpathgroups.ExternalClasspathGroupDTO;
import net.fabricmc.loom.task.AbstractLoomTask;

public abstract class ExportClasspathTask extends AbstractLoomTask {
	@Input
	public abstract Property<String> getClasspathDtoJson();

	@OutputFile
	public abstract RegularFileProperty getOutput();

	@Inject
	protected abstract WorkerExecutor getWorkerExecutor();

	@Inject
	public ExportClasspathTask() {
		getClasspathDtoJson().set(getProject()
				.provider(() -> ExternalClasspathGroupDTO.createFromProject(getProject()))
				.map(LoomGradlePlugin.GSON::toJson));
		getOutput().set(getProject().getLayout().getBuildDirectory().file("export_classpath.json"));
	}

	@TaskAction
	public void run() {
		final WorkQueue workQueue = getWorkerExecutor().noIsolation();
		workQueue.submit(ExportClassPathWorkAction.class, p -> {
			p.getClasspathDtoJson().set(getClasspathDtoJson());
			p.getOutput().set(getOutput());
		});
	}

	protected interface ExportClassPathWorkParameters extends WorkParameters {
		Property<String> getClasspathDtoJson();
		RegularFileProperty getOutput();
	}

	public abstract static class ExportClassPathWorkAction implements WorkAction<ExportClassPathWorkParameters> {
		@Override
		public void execute() {
			File outputFile = getParameters().getOutput().getAsFile().get();
			String json = getParameters().getClasspathDtoJson().get();

			try {
				Files.writeString(outputFile.toPath(), json);
			} catch (IOException e) {
				throw new UncheckedIOException("Failed to write classpath groups", e);
			}
		}
	}
}
