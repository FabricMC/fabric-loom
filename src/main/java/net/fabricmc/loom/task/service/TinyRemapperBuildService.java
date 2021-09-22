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

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.tooling.events.FinishEvent;
import org.gradle.tooling.events.OperationCompletionListener;

import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.extension.mixin.MixinExtension;

public abstract class TinyRemapperBuildService implements BuildService<TinyRemapperBuildService.Params>, OperationCompletionListener, AutoCloseable {
	public interface Params extends BuildServiceParameters {
		ListProperty<IMappingProvider> getMappings();

		// classpath passed into the TinyRemapper instance
		ConfigurableFileCollection getClasspath();

		Property<Boolean> getUseMixinExtension();
	}

	private final TinyRemapper tinyRemapper;
	private boolean closed = false;

	public TinyRemapperBuildService() {
		this.tinyRemapper = createTinyRemapper();
	}

	private TinyRemapper createTinyRemapper() {
		final Params params = getParameters();
		TinyRemapper.Builder builder = TinyRemapper.newRemapper();

		// Apply mappings
		for (IMappingProvider mappingProvider : params.getMappings().get()) {
			builder.withMappings(mappingProvider);
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
	public void onFinish(FinishEvent finishEvent) {
		close();
	}

	@Override
	public void close() {
		if (closed) {
			return;
		}

		closed = true;
		tinyRemapper.finish();
	}

	public TinyRemapper getTinyRemapper() {
		if (closed) {
			throw new RuntimeException("Cannot access closed tiny remapper");
		}

		return tinyRemapper;
	}
}
