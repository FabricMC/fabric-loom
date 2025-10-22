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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;

import net.fabricmc.classtweaker.api.ClassTweakerReader;
import net.fabricmc.classtweaker.validator.ClassTweakerValidatingVisitor;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.util.TinyRemapperLoggerAdapter;
import net.fabricmc.tinyremapper.TinyRemapper;

public abstract class ValidateAccessWidenerTask extends DefaultTask {
	@SkipWhenEmpty
	@InputFile
	public abstract RegularFileProperty getAccessWidener();

	@InputFiles
	public abstract ConfigurableFileCollection getTargetJars();

	@Inject
	public ValidateAccessWidenerTask() {
		final LoomGradleExtension extension = LoomGradleExtension.get(getProject());

		getAccessWidener().convention(extension.getAccessWidenerPath()).finalizeValueOnRead();
		getTargetJars().from(extension.getMinecraftJarsCollection(MappingsNamespace.NAMED));

		// Ignore outputs for up-to-date checks as there aren't any (so only inputs are checked)
		getOutputs().upToDateWhen(task -> true);
	}

	@TaskAction
	public void run() {
		final TinyRemapper tinyRemapper = TinyRemapper.newRemapper(TinyRemapperLoggerAdapter.INSTANCE).build();

		for (File file : getTargetJars().getFiles()) {
			tinyRemapper.readClassPath(file.toPath());
		}

		AtomicBoolean hasProblem = new AtomicBoolean(false);

		final ClassTweakerValidatingVisitor validator = new ClassTweakerValidatingVisitor(tinyRemapper.getEnvironment(), (lineNumber, message) -> {
			hasProblem.set(true);
			getLogger().error("{} on line {}", message, lineNumber);
		});

		final ClassTweakerReader accessWidenerReader = ClassTweakerReader.create(validator);

		try (BufferedReader reader = Files.newBufferedReader(getAccessWidener().get().getAsFile().toPath(), StandardCharsets.UTF_8)) {
			accessWidenerReader.read(reader, "named");
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to read access widener", e);
		} finally {
			tinyRemapper.finish();
		}

		if (hasProblem.get()) {
			getLogger().error("access-widener validation failed for {}", getAccessWidener().get().getAsFile().getName());
			throw new RuntimeException("access-widener validation failed for %s".formatted(getAccessWidener().get().getAsFile().getName()));
		}
	}
}
