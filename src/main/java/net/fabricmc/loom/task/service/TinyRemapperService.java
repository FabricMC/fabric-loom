/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022 FabricMC
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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.gradle.api.Project;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.kotlin.remapping.KotlinMetadataTinyRemapperExtension;
import net.fabricmc.loom.task.AbstractRemapJarTask;
import net.fabricmc.loom.util.service.SharedService;
import net.fabricmc.loom.util.service.SharedServiceManager;
import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.InputTag;
import net.fabricmc.tinyremapper.TinyRemapper;

public class TinyRemapperService implements SharedService {
	public static synchronized TinyRemapperService getOrCreate(AbstractRemapJarTask remapJarTask) {
		final Project project = remapJarTask.getProject();
		final String to = remapJarTask.getTargetNamespace().get();
		final String from = remapJarTask.getSourceNamespace().get();
		final LoomGradleExtension extension = LoomGradleExtension.get(project);
		final SharedServiceManager sharedServiceManager = SharedServiceManager.get(project);
		final boolean legacyMixin = extension.getMixin().getUseLegacyMixinAp().get();
		final boolean useKotlinExtension = project.getPluginManager().hasPlugin("org.jetbrains.kotlin.jvm");

		// Generates an id that is used to share the remapper across projects. This tasks in the remap jar task name to handle custom remap jar tasks separately.
		final String id = extension.getMappingsProvider().getBuildServiceName("remapJarService", from, to) + ":" + remapJarTask.getName() + (useKotlinExtension ? ":kotlin" : "");

		TinyRemapperService service = sharedServiceManager.getOrCreateService(id, () -> {
			List<IMappingProvider> mappings = new ArrayList<>();
			mappings.add(MappingsService.createDefault(project, from, to).getMappingsProvider());

			if (legacyMixin) {
				mappings.add(MixinMappingsService.getService(SharedServiceManager.get(project)).getMappingProvider(from, to));
			}

			return new TinyRemapperService(mappings, !legacyMixin, useKotlinExtension);
		});

		service.readClasspath(remapJarTask.getClasspath().getFiles().stream().map(File::toPath).toList());

		return service;
	}

	private TinyRemapper tinyRemapper;
	private final Map<String, InputTag> inputTagMap = new HashMap<>();
	private final HashSet<Path> classpath = new HashSet<>();
	// Set to true once remapping has started, once set no inputs can be read.
	private boolean isRemapping = false;

	public TinyRemapperService(List<IMappingProvider> mappings, boolean useMixinExtension, boolean useKotlinExtension) {
		TinyRemapper.Builder builder = TinyRemapper.newRemapper();

		for (IMappingProvider provider : mappings) {
			builder.withMappings(provider);
		}

		if (useMixinExtension) {
			builder.extension(new net.fabricmc.tinyremapper.extension.mixin.MixinExtension());
		}

		if (useKotlinExtension) {
			builder.extension(KotlinMetadataTinyRemapperExtension.INSTANCE);
		}

		tinyRemapper = builder.build();
	}

	public synchronized InputTag getOrCreateTag(Path file) {
		InputTag tag = inputTagMap.get(file.toAbsolutePath().toString());

		if (tag == null) {
			tag = tinyRemapper.createInputTag();
			inputTagMap.put(file.toAbsolutePath().toString(), tag);
		}

		return tag;
	}

	public TinyRemapper getTinyRemapperForRemapping() {
		synchronized (this) {
			isRemapping = true;
			return Objects.requireNonNull(tinyRemapper, "Tiny remapper has not been setup");
		}
	}

	public synchronized TinyRemapper getTinyRemapperForInputs() {
		synchronized (this) {
			if (isRemapping) {
				throw new IllegalStateException("Cannot read inputs as remapping has already started");
			}

			return tinyRemapper;
		}
	}

	void readClasspath(List<Path> paths) {
		List<Path> toRead;

		synchronized (classpath) {
			toRead = paths.stream().filter(path -> !classpath.contains(path)).toList();
			classpath.addAll(paths);
		}

		tinyRemapper.readClassPathAsync(toRead.toArray(Path[]::new));
	}

	@Override
	public void close() throws IOException {
		if (tinyRemapper != null) {
			tinyRemapper.finish();
			tinyRemapper = null;
		}
	}
}
