/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2024 FabricMC
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

package net.fabricmc.loom.task.service;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.SourceSet;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.build.mixin.AnnotationProcessorInvoker;
import net.fabricmc.loom.util.TinyRemapperHelper;
import net.fabricmc.loom.util.gradle.GradleUtils;
import net.fabricmc.loom.util.gradle.SourceSetHelper;
import net.fabricmc.loom.util.service.Service;
import net.fabricmc.loom.util.service.ServiceFactory;
import net.fabricmc.loom.util.service.ServiceType;
import net.fabricmc.tinyremapper.IMappingProvider;

public class MixinAPMappingService extends Service<MixinAPMappingService.Options> {
	public static final ServiceType<Options, MixinAPMappingService> TYPE = new ServiceType<>(Options.class, MixinAPMappingService.class);

	// Again look into what the result of changing this would be.
	private static final boolean USE_ALL_SOURCE_SETS = true;
	private static final Logger LOGGER = LoggerFactory.getLogger(MixinAPMappingService.class);

	public interface Options extends Service.Options {
		@InputFiles // We need to depend on all the outputs, as we don't know if the mixin mapping will exist at the time of task creation
		ConfigurableFileCollection getCompileOutputs();
		@Input
		Property<String> getMixinMappingFileName();
		@Input
		Property<String> getFrom();
		@Input
		Property<String> getTo();
	}

	public static Provider<List<Options>> createOptions(Project thisProject, Provider<String> from, Provider<String> to) {
		final LoomGradleExtension thisExtension = LoomGradleExtension.get(thisProject);
		String mappingId = thisExtension.getMappingConfiguration().mappingsIdentifier;

		var providers = new ArrayList<Provider<Options>>();

		Consumer<Project> processProject = project -> {
			final LoomGradleExtension extension = LoomGradleExtension.get(project);

			Collection<SourceSet> sourceSets = USE_ALL_SOURCE_SETS ? SourceSetHelper.getSourceSets(project) : extension.getMixin().getMixinSourceSets();

			for (SourceSet sourceSet : sourceSets) {
				LOGGER.debug("Creating MixinAPMappingService for source set: {}", sourceSet.getName());

				var provider = createOptions(
						thisProject,
						sourceSet,
						from,
						to
				);

				if (provider != null) {
					providers.add(provider);
				} else {
					LOGGER.debug("Failed to create MixinAPMappingService for source set: {}", sourceSet.getName());
				}
			}
		};

		if (thisExtension.isProjectIsolationActive()) {
			// TODO provide a project isolated way of remapping with dependency mixin mapping
			processProject.accept(thisProject);
		} else {
			GradleUtils.allLoomProjects(thisProject.getGradle(), project -> {
				final LoomGradleExtension extension = LoomGradleExtension.get(project);

				if (!mappingId.equals(extension.getMappingConfiguration().mappingsIdentifier)) {
					// Only find mixin mappings that are from other projects with the same mapping id.
					return;
				}

				processProject.accept(project);
			});
		}

		return thisProject.provider(() -> providers.stream().map(Provider::get).toList());
	}

	@Nullable
	public static Provider<Options> createOptions(Project project, SourceSet sourceSet, Provider<String> from, Provider<String> to) {
		final File mixinMappings = AnnotationProcessorInvoker.getMixinMappingsForSourceSet(project, sourceSet);
		final Task compileTask = project.getTasks().findByName(sourceSet.getCompileJavaTaskName()); // TODO what about other languages?

		if (compileTask == null) {
			return null;
		}

		boolean containsOutput = false;

		for (File file : compileTask.getOutputs().getFiles()) {
			if (file.getName().equals(mixinMappings.getName())) {
				containsOutput = true;
				break;
			}
		}

		if (!containsOutput) {
			LOGGER.warn("Failed to find mixin mappings {} in task outputs: {}", mixinMappings.getName(), compileTask.getOutputs().getFiles());
			return null;
		}

		return TYPE.create(project, o -> {
			o.getCompileOutputs().from(compileTask.getOutputs());
			o.getMixinMappingFileName().set(mixinMappings.getName());
			o.getFrom().set(from);
			o.getTo().set(to);
		});
	}

	private IMappingProvider mappingProvider = null;
	private boolean exists = true;

	public MixinAPMappingService(Options options, ServiceFactory serviceFactory) {
		super(options, serviceFactory);
	}

	@Nullable
	public IMappingProvider getMappingsProvider() {
		if (!exists) {
			return null;
		}

		if (mappingProvider == null) {
			final Path mappingsPath = getMappingsPath();

			if (!Files.exists(mappingsPath)) {
				exists = false;
				return null;
			}

			try {
				mappingProvider = TinyRemapperHelper.create(
						mappingsPath,
						getOptions().getFrom().get(),
						getOptions().getTo().get(),
						false
				);
			} catch (IOException e) {
				throw new UncheckedIOException("Failed to read mappings from: " + mappingsPath, e);
			}
		}

		return mappingProvider;
	}

	// We should always find the file in the task outputs, regardless of if it exists or not.
	private Path getMappingsPath() {
		for (File file : getOptions().getCompileOutputs().getFiles()) {
			if (file.getName().equals(getOptions().getMixinMappingFileName().get())) {
				return file.toPath();
			}
		}

		throw new RuntimeException("Failed to find mixin mappings file: " + getOptions().getMixinMappingFileName().get());
	}
}
