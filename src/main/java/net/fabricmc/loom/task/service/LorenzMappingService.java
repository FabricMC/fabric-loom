/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2023 FabricMC
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
import java.util.function.Supplier;

import com.google.common.base.Suppliers;
import org.cadixdev.lorenz.MappingSet;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Nested;

import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.providers.mappings.MappingConfiguration;
import net.fabricmc.loom.util.service.Service;
import net.fabricmc.loom.util.service.ServiceFactory;
import net.fabricmc.loom.util.service.ServiceType;
import net.fabricmc.lorenztiny.TinyMappingsReader;

public final class LorenzMappingService extends Service<LorenzMappingService.Options> {
	public static final ServiceType<Options, LorenzMappingService> TYPE = new ServiceType<>(Options.class, LorenzMappingService.class);

	public interface Options extends Service.Options {
		@Nested
		Property<MappingsService.Options> getMappings();
	}

	public static Provider<Options> createOptions(Project project, MappingConfiguration mappingConfiguration, MappingsNamespace from, MappingsNamespace to) {
		return TYPE.create(project, options -> options.getMappings().set(
			MappingsService.createOptions(
				project,
				mappingConfiguration.tinyMappings,
				to.toString(),
				from.toString(),
				false)
		));
	}

	private final Supplier<MappingSet> mappings = Suppliers.memoize(this::readMappings);

	public LorenzMappingService(Options options, ServiceFactory serviceFactory) {
		super(options, serviceFactory);
	}

	private MappingSet readMappings() {
		MappingsService mappingsService = getServiceFactory().get(getOptions().getMappings().get());

		try {
			try (var reader = new TinyMappingsReader(mappingsService.getMemoryMappingTree(), mappingsService.getFrom(), mappingsService.getTo())) {
				return reader.read();
			}
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to read lorenz mappings", e);
		}
	}

	public MappingSet getMappings() {
		return mappings.get();
	}
}
