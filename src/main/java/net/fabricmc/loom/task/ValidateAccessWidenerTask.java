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
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import javax.inject.Inject;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;

import net.fabricmc.accesswidener.AccessWidenerFormatException;
import net.fabricmc.accesswidener.AccessWidenerReader;
import net.fabricmc.accesswidener.AccessWidenerVisitor;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.api.TrEnvironment;

public abstract class ValidateAccessWidenerTask extends DefaultTask {
	@SkipWhenEmpty
	@InputFile
	public abstract RegularFileProperty getAccessWidener();

	@InputFile
	public abstract RegularFileProperty getTargetJar();

	@Inject
	public ValidateAccessWidenerTask() {
		final LoomGradleExtension extension = LoomGradleExtension.get(getProject());

		getAccessWidener().convention(extension.getAccessWidenerPath()).finalizeValueOnRead();
		getTargetJar().convention(getProject().getObjects().fileProperty().fileValue(extension.getMinecraftMappedProvider().getMappedJar())).finalizeValueOnRead();
	}

	@TaskAction
	public void run() {
		final TinyRemapper tinyRemapper = TinyRemapper.newRemapper().build();
		tinyRemapper.readClassPath(getTargetJar().get().getAsFile().toPath());

		final AccessWidenerValidator validator = new AccessWidenerValidator(tinyRemapper.getEnvironment());
		final AccessWidenerReader accessWidenerReader = new AccessWidenerReader(validator);

		try (BufferedReader reader = Files.newBufferedReader(getAccessWidener().get().getAsFile().toPath(), StandardCharsets.UTF_8)) {
			accessWidenerReader.read(reader, "named");
		} catch (AccessWidenerFormatException e) {
			getProject().getLogger().error("Failed to validate access-widener file {} on line {}: {}", getAccessWidener().get().getAsFile().getName(), e.getLineNumber(), e.getMessage());
			throw e;
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to read access widener", e);
		} finally {
			tinyRemapper.finish();
		}
	}

	/**
	 * Validates that all entries in an access-widner file relate to a class/method/field in the mc jar.
	 */
	private static record AccessWidenerValidator(TrEnvironment environment) implements AccessWidenerVisitor {
		@Override
		public void visitClass(String name, AccessWidenerReader.AccessType access, boolean transitive) {
			if (environment().getClass(name) == null) {
				throw new RuntimeException("Could not find class (%s)".formatted(name));
			}
		}

		@Override
		public void visitMethod(String owner, String name, String descriptor, AccessWidenerReader.AccessType access, boolean transitive) {
			if (environment().getMethod(owner, name, descriptor) == null) {
				throw new RuntimeException("Could not find method (%s%s) in class (%s)".formatted(name, descriptor, owner));
			}
		}

		@Override
		public void visitField(String owner, String name, String descriptor, AccessWidenerReader.AccessType access, boolean transitive) {
			if (environment().getField(owner, name, descriptor) == null) {
				throw new RuntimeException("Could not find field (%s%s) in class (%s)".formatted(name, descriptor, owner));
			}
		}
	}
}
