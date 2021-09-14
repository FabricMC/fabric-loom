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

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.util.SourceRemapper;

public class RemapSourcesJarTask extends AbstractLoomTask {
	private final RegularFileProperty input = getProject().getObjects().fileProperty();
	private final RegularFileProperty output = getProject().getObjects().fileProperty().convention(input);
	private final Property<String> targetNamespace = getProject().getObjects().property(String.class).convention(MappingsNamespace.INTERMEDIARY.toString());
	private SourceRemapper sourceRemapper = null;
	private final Property<Boolean> preserveFileTimestamps = getProject().getObjects().property(Boolean.class).convention(true);
	private final Property<Boolean> reproducibleFileOrder = getProject().getObjects().property(Boolean.class).convention(false);

	public RemapSourcesJarTask() {
	}

	@TaskAction
	public void remap() throws Exception {
		if (sourceRemapper == null) {
			String direction = targetNamespace.get();
			SourceRemapper.remapSources(getProject(), input.get().getAsFile(), output.get().getAsFile(), direction.equals(MappingsNamespace.NAMED.toString()), reproducibleFileOrder.get(), preserveFileTimestamps.get());
		} else {
			sourceRemapper.scheduleRemapSources(input.get().getAsFile(), output.get().getAsFile(), reproducibleFileOrder.get(), preserveFileTimestamps.get());
		}
	}

	@Internal
	public SourceRemapper getSourceRemapper() {
		return sourceRemapper;
	}

	public RemapSourcesJarTask setSourceRemapper(SourceRemapper sourceRemapper) {
		this.sourceRemapper = sourceRemapper;
		return this;
	}

	@InputFile
	public RegularFileProperty getInput() {
		return input;
	}

	@OutputFile
	public RegularFileProperty getOutput() {
		return output;
	}

	@Input
	public Property<String> getTargetNamespace() {
		return targetNamespace;
	}

	@Input
	public Property<Boolean> getPreserveFileTimestamps() {
		return preserveFileTimestamps;
	}

	@Input
	public Property<Boolean> getReproducibleFileOrder() {
		return reproducibleFileOrder;
	}
}
