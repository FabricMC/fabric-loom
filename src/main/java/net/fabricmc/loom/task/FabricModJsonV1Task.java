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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;

import net.fabricmc.loom.api.fmj.FabricModJsonV1Spec;
import net.fabricmc.loom.util.fmj.gen.FabricModJsonV1Generator;

/**
 * A task that generates a {@code fabric.mod.json} file using the configured {@link FabricModJsonV1Spec} specification.
 */
public abstract class FabricModJsonV1Task extends AbstractLoomTask {
	/**
	 * The fabric.mod.json spec.
	 */
	@Nested
	public abstract Property<FabricModJsonV1Spec> getJson();

	/**
	 * The output file to write the generated fabric.mod.json to.
	 */
	@OutputFile
	public abstract RegularFileProperty getOutputFile();

	@Inject
	protected abstract WorkerExecutor getWorkerExecutor();

	@Inject
	protected abstract ObjectFactory getObjectFactory();

	public FabricModJsonV1Task() {
		getJson().set(getObjectFactory().newInstance(FabricModJsonV1Spec.class));
	}

	/**
	 * Configure the fabric.mod.json spec.
	 *
	 * @param action A {@link Action} that configures the spec.
	 */
	public void json(Action<FabricModJsonV1Spec> action) {
		action.execute(getJson().get());
	}

	@TaskAction
	public void run() {
		final WorkQueue workQueue = getWorkerExecutor().noIsolation();

		workQueue.submit(FabricModJsonV1WorkAction.class, params -> {
			params.getSpec().set(getJson());
			params.getOutputFile().set(getOutputFile());
		});
	}

	public interface FabricModJsonV1WorkParameters extends WorkParameters {
		Property<FabricModJsonV1Spec> getSpec();

		RegularFileProperty getOutputFile();
	}

	public abstract static class FabricModJsonV1WorkAction implements WorkAction<FabricModJsonV1WorkParameters> {
		@Override
		public void execute() {
			FabricModJsonV1Spec spec = getParameters().getSpec().get();
			Path outputPath = getParameters().getOutputFile().get().getAsFile().toPath();

			String json = FabricModJsonV1Generator.INSTANCE.generate(spec);

			try {
				Files.writeString(outputPath, json);
			} catch (IOException e) {
				throw new UncheckedIOException("Failed to write fabric.mod.json", e);
			}
		}
	}
}
