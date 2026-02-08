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

import java.nio.file.Files;
import java.nio.file.Path;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;
import org.gradle.api.tasks.options.Option;

import net.fabricmc.classtweaker.api.ClassTweakerReader;
import net.fabricmc.classtweaker.api.ClassTweakerWriter;
import net.fabricmc.classtweaker.visitors.ClassTweakerRemapperVisitor;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.task.service.MigrateClassTweakerMappingsService;
import net.fabricmc.loom.util.service.ScopedServiceFactory;

@UntrackedTask(because = "Always rerun this task.")
public abstract class MigrateClassTweakerMappingsTask extends AbstractMigrateMappingsTask {
	@InputFile
	@SkipWhenEmpty
	@PathSensitive(PathSensitivity.NONE)
	@Option(option = "input", description = "Access widener file")
	public abstract RegularFileProperty getInputFile();

	@OutputFile
	@Option(option = "output", description = "Remapped access widener file")
	public abstract RegularFileProperty getOutputFile();

	@Nested
	protected abstract Property<MigrateClassTweakerMappingsService.Options> getMigrationServiceOptions();

	public MigrateClassTweakerMappingsTask() {
		getInputFile().convention(getExtension().getAccessWidenerPath());
		getOutputFile().convention(getProject().getLayout().getProjectDirectory().file("remapped.accesswidener"));
		getMigrationServiceOptions().set(MigrateClassTweakerMappingsService.createOptions(getProject(), getMappings()));
	}

	@TaskAction
	public void doTask() throws Throwable {
		try (var serviceFactory = new ScopedServiceFactory()) {
			final MigrateClassTweakerMappingsService service = serviceFactory.get(getMigrationServiceOptions().get());
			final Path inputFile = getInputFile().get().getAsFile().toPath();
			final byte[] inputBytes = Files.readAllBytes(inputFile);
			final int ctVersion = ClassTweakerReader.readVersion(inputBytes);

			final ClassTweakerWriter writer = ClassTweakerWriter.create(ctVersion);
			final var remapper = new ClassTweakerRemapperVisitor(writer, service.getRemapper(), MappingsNamespace.NAMED.toString(), MappingsNamespace.NAMED.toString());
			ClassTweakerReader.create(remapper).read(inputBytes, "unused_id");

			final boolean inPlace = getOverrideInputs().get();
			final Path targetFile = inPlace ? inputFile : getOutputFile().get().getAsFile().toPath();
			Files.write(targetFile, writer.getOutput());
		}
	}
}
