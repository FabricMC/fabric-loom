/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021 FabricMC
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

import java.io.IOException;
import java.nio.file.Path;

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.jvm.tasks.Jar;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;

import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.util.ZipReprocessorUtil;

public abstract class AbstractRemapJarTask extends Jar {
	@InputFile
	public abstract RegularFileProperty getInputFile();

	@InputFiles
	public abstract ConfigurableFileCollection getClasspath();

	@Input
	public abstract Property<String> getSourceNamespace();

	@Input
	public abstract Property<String> getTargetNamespace();

	@Inject
	protected abstract WorkerExecutor getWorkerExecutor();

	@Inject
	public AbstractRemapJarTask() {
		getSourceNamespace().convention(MappingsNamespace.NAMED.toString()).finalizeValueOnRead();
		getTargetNamespace().convention(MappingsNamespace.INTERMEDIARY.toString()).finalizeValueOnRead();
	}

	public final <P extends AbstractRemapParams> void submitWork(Class<? extends AbstractRemapAction<P>> workAction, Action<P> action) {
		final WorkQueue workQueue = getWorkerExecutor().noIsolation();

		workQueue.submit(workAction, params -> {
			params.getInputFile().set(getInputFile());
			params.getOutputFile().set(getArchiveFile());

			params.getSourceNamespace().set(getSourceNamespace());
			params.getTargetNamespace().set(getTargetNamespace());

			params.getArchivePreserveFileTimestamps().set(isPreserveFileTimestamps());
			params.getArchiveReproducibleFileOrder().set(isReproducibleFileOrder());

			action.execute(params);
		});
	}

	public interface AbstractRemapParams extends WorkParameters {
		RegularFileProperty getInputFile();
		RegularFileProperty getOutputFile();

		Property<String> getSourceNamespace();
		Property<String> getTargetNamespace();

		Property<Boolean> getArchivePreserveFileTimestamps();
		Property<Boolean> getArchiveReproducibleFileOrder();
	}

	public abstract static class AbstractRemapAction<T extends AbstractRemapParams> implements WorkAction<T> {
		protected final Path inputFile;
		protected final Path outputFile;

		@Inject
		public AbstractRemapAction() {
			inputFile = getParameters().getInputFile().getAsFile().get().toPath();
			outputFile = getParameters().getOutputFile().getAsFile().get().toPath();
		}

		protected void rewriteJar() throws IOException {
			final boolean isReproducibleFileOrder = getParameters().getArchiveReproducibleFileOrder().get();
			final boolean isPreserveFileTimestamps = getParameters().getArchivePreserveFileTimestamps().get();

			if (isReproducibleFileOrder || !isPreserveFileTimestamps) {
				ZipReprocessorUtil.reprocessZip(outputFile.toFile(), isReproducibleFileOrder, isPreserveFileTimestamps);
			}
		}
	}

	@Deprecated
	@InputFile
	public RegularFileProperty getInput() {
		return getInputFile();
	}
}
