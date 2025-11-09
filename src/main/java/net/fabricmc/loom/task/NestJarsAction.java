/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2021 FabricMC
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
import java.io.Serializable;
import java.util.Set;

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFiles;
import org.gradle.jvm.tasks.Jar;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.build.nesting.JarNester;

/**
 * Configuration-cache-compatible action for nesting jars.
 * Uses a FileCollection to avoid capturing task references at configuration time.
 * Do NOT turn me into a record!
 */
public abstract class NestJarsAction implements Action<Task>, Serializable {
	@InputFiles
	public abstract ConfigurableFileCollection getJars();

	@Inject
	protected abstract WorkerExecutor getWorkerExecutor();

	public static void addToTask(Jar task, FileCollection jars) {
		NestJarsAction nestJarsAction = task.getProject().getObjects().newInstance(NestJarsAction.class);
		nestJarsAction.getJars().from(jars);
		task.getInputs().files(nestJarsAction.getJars()); // I don't think @InputFiles works, so to be sure add the jars to the task input anyway.
		task.doLast(nestJarsAction);
	}

	@Override
	public void execute(@NotNull Task t) {
		final Jar jarTask = (Jar) t;

		final WorkQueue workQueue = getWorkerExecutor().noIsolation();

		workQueue.submit(NestAction.class, p -> {
			p.getArchiveFile().set(jarTask.getArchiveFile());
			p.getJars().setFrom(getJars());
		});
	}

	public interface NestJarsParameters extends WorkParameters {
		RegularFileProperty getArchiveFile();
		ConfigurableFileCollection getJars();
	}

	public abstract static class NestAction implements WorkAction<NestJarsParameters> {
		private static final Logger LOGGER = LoggerFactory.getLogger(NestJarsAction.class);

		@Override
		public void execute() {
			final File jarFile = getParameters().getArchiveFile().get().getAsFile();
			final Set<File> jars = getParameters().getJars().getFiles();

			// Nest all collected jars
			if (!jars.isEmpty()) {
				JarNester.nestJars(jars, jarFile, LOGGER);
				LOGGER.info("Nested {} jar(s) into {}", jars.size(), jarFile.getName());
			}
		}
	}
}
