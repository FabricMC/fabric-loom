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

package net.fabricmc.loom.task.service;

import java.io.IOException;

import org.gradle.api.Project;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;

import net.fabricmc.loom.util.service.ServiceFactory;
import net.fabricmc.loom.util.service.ServiceType;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

/**
 * A mappings service that joins multiple mappings services into one.
 */
public final class JoinedMappingsService extends AbstractMappingsService<JoinedMappingsService.Options> {
	public static final ServiceType<Options, JoinedMappingsService> TYPE = new ServiceType<>(Options.class, JoinedMappingsService.class);

	public interface Options extends AbstractMappingsService.Options {
		@Nested
		ListProperty<AbstractMappingsService.Options> getMappings();
		@Input
		Property<String> getCommonNamespace();
	}

	@SafeVarargs
	public static Provider<Options> createOptions(Project project, String commonNamespace, Provider<? extends AbstractMappingsService.Options>... mappings) {
		return TYPE.create(project, options -> {
			for (Provider<? extends AbstractMappingsService.Options> mapping : mappings) {
				options.getMappings().add(mapping);
			}

			options.getCommonNamespace().set(commonNamespace);
		});
	}

	public JoinedMappingsService(Options options, ServiceFactory serviceFactory) {
		super(options, serviceFactory);
	}

	@Override
	protected MemoryMappingTree buildMemoryMappingTree() {
		MemoryMappingTree mappingTree = new MemoryMappingTree();

		for (AbstractMappingsService.Options mappingOptions : getOptions().getMappings().get()) {
			final AbstractMappingsService<?> mappingsService = (AbstractMappingsService<?>) getServiceFactory().get(mappingOptions);

			try {
				var nsSwitch = new MappingSourceNsSwitch(mappingTree, getOptions().getCommonNamespace().get());
				mappingsService.getMemoryMappingTree().accept(nsSwitch);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}

		return mappingTree;
	}
}
