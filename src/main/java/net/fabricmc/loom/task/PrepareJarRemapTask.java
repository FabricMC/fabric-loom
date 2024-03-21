/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022 FabricMC
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

import java.nio.file.Path;

import javax.inject.Inject;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;

import net.fabricmc.loom.task.service.TinyRemapperService;
import net.fabricmc.loom.util.service.UnsafeWorkQueueHelper;

/**
 * The prepare remap task runs before all other jar remap tasks, should be used to setup tiny remapper.
 */
public abstract class PrepareJarRemapTask extends AbstractLoomTask {
	private final RemapJarTask remapJarTask;
	@InputFile
	public abstract RegularFileProperty getInputFile();

	@Inject
	public PrepareJarRemapTask(RemapJarTask remapJarTask) {
		this.remapJarTask = remapJarTask;

		getInputFile().set(remapJarTask.getInputFile());
		// TODO can this be up-to-date when the main task is up-to date?
		getOutputs().upToDateWhen((o) -> false);

		getProject().getGradle().allprojects(project -> {
			project.getTasks().withType(PrepareJarRemapTask.class, otherTask -> {
				if (otherTask == this) return;

				// Ensure that all other prepare tasks inputs have completed
				dependsOn(otherTask.getInputs());
				mustRunAfter(otherTask.getInputs());
			});
		});
	}

	@Inject
	protected abstract WorkerExecutor getWorkerExecutor();

	@TaskAction
	public void run() {
		final WorkQueue workQueue = getWorkerExecutor().noIsolation();

		workQueue.submit(ReadInputsAction.class, params -> {
			params.getTinyRemapperBuildServiceUuid().set(UnsafeWorkQueueHelper.create(remapJarTask.getTinyRemapperService()));
			params.getInputFile().set(getInputFile());
		});
	}

	public interface ReadInputsParams extends WorkParameters {
		Property<String> getTinyRemapperBuildServiceUuid();
		RegularFileProperty getInputFile();
	}

	public abstract static class ReadInputsAction implements WorkAction<ReadInputsParams> {
		private final TinyRemapperService tinyRemapperService;

		public ReadInputsAction() {
			this.tinyRemapperService = UnsafeWorkQueueHelper.get(getParameters().getTinyRemapperBuildServiceUuid(), TinyRemapperService.class);
		}

		@Override
		public void execute() {
			final Path inputFile = getParameters().getInputFile().getAsFile().get().toPath();
			prepare(tinyRemapperService, inputFile);
		}
	}

	static void prepare(TinyRemapperService tinyRemapperService, Path inputFile) {
		tinyRemapperService.getTinyRemapperForInputs().readInputsAsync(tinyRemapperService.getOrCreateTag(inputFile), inputFile);
	}
}
