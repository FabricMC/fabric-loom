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

import java.io.File;

import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.extension.mixin.MixinExtension;

public abstract class TinyRemapperService implements BuildService<TinyRemapperService.Params>, AutoCloseable {
	public interface Params extends BuildServiceParameters {
		ListProperty<Provider<MappingsService>> getMappings();

		// classpath passed into the TinyRemapper instance
		ConfigurableFileCollection getClasspath();

		Property<Boolean> getUseMixinExtension();
	}

	public static synchronized Provider<TinyRemapperService> create(Project project, String from, String to) {
		final LoomGradleExtension extension = LoomGradleExtension.get(project);

		return project.getGradle().getSharedServices().registerIfAbsent(extension.getMappingsProvider().getBuildServiceName("remapJarService", from, to), TinyRemapperService.class, spec -> {
			spec.parameters(params -> {
				params.getClasspath().plus(project.getConfigurations().getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME));
				params.getMappings().add(MappingsService.createDefault(project, from, to));

				final boolean legacyMixin = extension.getMixin().getUseLegacyMixinAp().get();
				params.getUseMixinExtension().set(!legacyMixin);

				if (legacyMixin) {
					// Add the mapping from the mixin AP
					for (File file : extension.getAllMixinMappings().getFiles()) {
						// TODO fix me, use the hash?
						String name = file.getAbsolutePath();
						params.getMappings().add(MappingsService.create(project, name, file, from, to, false));
					}
				}
			});
		});
	}

	private TinyRemapper tinyRemapper;
	private boolean finished = false;

	private TinyRemapper createTinyRemapper() {
		final Params params = getParameters();
		TinyRemapper.Builder builder = TinyRemapper.newRemapper();

		builder.keepInputData(true);

		// Apply mappings

		for (Provider<MappingsService> provider : params.getMappings().get()) {
			builder.withMappings(provider.get().getMappingsProvider());
		}

		if (params.getUseMixinExtension().get()) {
			builder.extension(new MixinExtension());
		}

		TinyRemapper remapper = builder.build();

		// Apply classpath
		for (File file : params.getClasspath()) {
			remapper.readClassPathAsync(file.toPath());
		}

		return remapper;
	}

	@Override
	public void close() {
		if (finished) {
			throw new RuntimeException("TinyRemapper already closed");
		}

		if (tinyRemapper != null) {
			tinyRemapper.finish();
		}

		finished = true;
	}

	public synchronized TinyRemapper getTinyRemapper() {
		if (finished) {
			throw new RuntimeException("TinyRemapper finished");
		}

		if (tinyRemapper == null) {
			this.tinyRemapper = createTinyRemapper();
		}

		return tinyRemapper;
	}
}
