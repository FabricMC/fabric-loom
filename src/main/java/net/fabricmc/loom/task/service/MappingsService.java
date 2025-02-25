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

package net.fabricmc.loom.task.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.providers.mappings.MappingConfiguration;
import net.fabricmc.loom.util.service.ServiceFactory;
import net.fabricmc.loom.util.service.ServiceType;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

/**
 * A service that provides mappings for remapping.
 */
public final class MappingsService extends AbstractMappingsService<MappingsService.Options> {
	public static ServiceType<Options, MappingsService> TYPE = new ServiceType<>(Options.class, MappingsService.class);

	// TODO use a nested TinyMappingsService instead of duplicating it
	public interface Options extends AbstractMappingsService.Options {
		@InputFile
		RegularFileProperty getMappingsFile();
		@Input
		Property<String> getFrom();
		@Input
		Property<String> getTo();
	}

	/**
	 * Returns options for creating a new mappings service, with a given mappings file.
	 */
	public static Provider<Options> createOptions(Project project, Path mappingsFile, String from, String to) {
		return createOptions(project, mappingsFile, project.provider(() -> from), project.provider(() -> to));
	}

	/**
	 * Returns options for creating a new mappings service, with a given mappings file.
	 */
	public static Provider<Options> createOptions(Project project, Path mappingsFile, Provider<String> from, Provider<String> to) {
		return TYPE.create(project, o -> {
			o.getMappingsFile().set(mappingsFile.toFile());
			o.getFrom().set(from);
			o.getTo().set(to);
		});
	}

	/**
	 * Returns options for creating a new mappings service, using the mappings as specified in the project's mapping configuration.
	 */
	public static Provider<Options> createOptions(Project project, Provider<String> from, Provider<String> to) {
		final MappingConfiguration mappingConfiguration = LoomGradleExtension.get(project).getMappingConfiguration();
		return createOptions(project, mappingConfiguration.tinyMappings, from, to);
	}

	/**
	 * Returns options for creating a new mappings service, using the mappings as specified in the project's mapping configuration.
	 */
	public static Provider<Options> createOptions(Project project, MappingsNamespace from, MappingsNamespace to) {
		return createOptions(project, project.provider(from::toString), project.provider(to::toString));
	}

	public MappingsService(Options options, ServiceFactory serviceFactory) {
		super(options, serviceFactory);
	}

	@Override
	public MemoryMappingTree buildMemoryMappingTree() {
		MemoryMappingTree mappingTree = new MemoryMappingTree();

		try {
			MappingReader.read(getMappingsPath(), mappingTree);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to read mappings from: " + getMappingsPath(), e);
		}

		return mappingTree;
	}

	public String getFrom() {
		return getOptions().getFrom().get();
	}

	public String getTo() {
		return getOptions().getTo().get();
	}

	public Path getMappingsPath() {
		return getOptions().getMappingsFile().get().getAsFile().toPath();
	}
}
