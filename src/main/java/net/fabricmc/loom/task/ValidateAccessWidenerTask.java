/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021-2023 FabricMC
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.resources.TextResource;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskAction;

import net.fabricmc.accesswidener.AccessWidenerFormatException;
import net.fabricmc.accesswidener.AccessWidenerReader;
import net.fabricmc.accesswidener.AccessWidenerVisitor;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.util.fmj.FabricModJson;
import net.fabricmc.loom.util.fmj.FabricModJsonHelpers;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.api.TrEnvironment;

public abstract class ValidateAccessWidenerTask extends DefaultTask {
	@Nested
	public abstract ListProperty<TextResource> getAccessWideners();

	@InputFiles
	public abstract ConfigurableFileCollection getTargetJars();

	@Inject
	public ValidateAccessWidenerTask() {
		final LoomGradleExtension extension = LoomGradleExtension.get(getProject());

		getAccessWideners().convention(getDefaultResources());
		getTargetJars().from(extension.getMinecraftJarsCollection(MappingsNamespace.NAMED));

		// Ignore outputs for up-to-date checks as there aren't any (so only inputs are checked)
		getOutputs().upToDateWhen(task -> true);
	}

	private Provider<List<TextResource>> getDefaultResources() {
		return getProject().provider(() -> {
			var resources = new ArrayList<TextResource>();

			for (FabricModJson fabricModJson : FabricModJsonHelpers.getModsInProject(getProject())) {
				Set<String> classTweakers = fabricModJson.getClassTweakers().keySet();

				for (String classTweaker : classTweakers) {
					var resource = fabricModJson.getSource().getTextResource(getProject().getResources().getText(), classTweaker);
					resources.add(resource);
				}
			}

			return resources;
		});
	}

	@TaskAction
	public void run() {
		final List<TextResource> accessWideners = getAccessWideners().get();

		if (accessWideners.isEmpty()) {
			return;
		}

		// TODO run async
		final TinyRemapper tinyRemapper = TinyRemapper.newRemapper().build();

		for (File file : getTargetJars().getFiles()) {
			tinyRemapper.readClassPath(file.toPath());
		}

		final AccessWidenerValidator validator = new AccessWidenerValidator(tinyRemapper.getEnvironment());
		final AccessWidenerReader accessWidenerReader = new AccessWidenerReader(validator);

		for (TextResource textResource : accessWideners) {
			try {
				accessWidenerReader.read(textResource.asString().getBytes(StandardCharsets.UTF_8), "named");
			} catch (AccessWidenerFormatException e) {
				getProject().getLogger().error("Failed to validate access-widener on line {}: {}", e.getLineNumber(), e.getMessage());
				throw e;
			}
		}
	}

	/**
	 * Validates that all entries in an access-widner file relate to a class/method/field in the mc jar.
	 */
	private record AccessWidenerValidator(TrEnvironment environment) implements AccessWidenerVisitor {
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
