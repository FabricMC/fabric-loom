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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

import org.gradle.api.Project;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.tasks.SourceSet;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.build.mixin.AnnotationProcessorInvoker;
import net.fabricmc.loom.task.AbstractRemapJarTask;
import net.fabricmc.loom.util.gradle.GradleUtils;
import net.fabricmc.loom.util.gradle.SourceSetHelper;
import net.fabricmc.loom.util.kotlin.KotlinClasspath;
import net.fabricmc.loom.util.kotlin.KotlinClasspathService;
import net.fabricmc.loom.util.kotlin.KotlinRemapperClassloader;
import net.fabricmc.loom.util.service.LoomServiceSpec;
import net.fabricmc.loom.util.service.ServiceFactory;
import net.fabricmc.loom.util.service.SharedService;
import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.InputTag;
import net.fabricmc.tinyremapper.TinyRemapper;

public class TinyRemapperService implements SharedService {
	public record Spec(
			String fromNs,
			String toNs,
			String taskName,
			@Nullable KotlinClasspathService.Spec kotlinClasspathSpec,
			@Nullable String isolationValue,
			MappingsService.Spec mappingsService,
			@Nullable List<MappingsService.Spec> mixinMappings,
			List<String> classpath
	) implements LoomServiceSpec<TinyRemapperService> {
		@Override
		public TinyRemapperService create(ServiceFactory serviceFactory) {
			final boolean legacyMixin = mixinMappings != null;

			var mappings = new ArrayList<IMappingProvider>();

			mappings.add(serviceFactory.getOrCreateService(mappingsService).getMappingsProvider());

			if (legacyMixin) {
				for (MappingsService.Spec mixinMapping : mixinMappings) {
					mappings.add(serviceFactory.getOrCreateService(mixinMapping).getMappingsProvider());
				}
			}

			return new TinyRemapperService(
					mappings,
					!legacyMixin,
					kotlinClasspathSpec == null ? null : serviceFactory.getOrCreateService(kotlinClasspathSpec),
					classpath.stream().map(Paths::get).toList()
			);
		}

		@Override
		public String getCacheKey() {
			var sj = new StringJoiner(":");
			sj.add("remapJarService");
			sj.add(fromNs);
			sj.add(toNs);
			sj.add(taskName);

			if (kotlinClasspathSpec != null) {
				sj.add(kotlinClasspathSpec.getCacheKey());
			}

			if (isolationValue != null) {
				sj.add(isolationValue);
			}

			sj.add(mappingsService().getCacheKey());

			if (mixinMappings != null) {
				// Null when not using legacy mixin mappings
				for (MappingsService.Spec mixinMapping : mixinMappings) {
					sj.add(mixinMapping.getCacheKey());
				}
			}

			return sj.toString();
		}
	}

	public static TinyRemapperService.Spec create(AbstractRemapJarTask remapJarTask) {
		final Project project = remapJarTask.getProject();
		final LoomGradleExtension extension = LoomGradleExtension.get(project);
		final boolean legacyMixin = extension.getMixin().getUseLegacyMixinAp().get();

		final String from = remapJarTask.getSourceNamespace().get();
		final String to = remapJarTask.getTargetNamespace().get();

		@Nullable String isolationValue = null;

		if (remapJarTask.getRemapperIsolation().get() || !extension.multiProjectOptimisation()) {
			isolationValue = project.getPath();
		}

		if (extension.multiProjectOptimisation()) {
			// TODO the classpath needs to be read for all remap jar tasks
			throw new UnsupportedOperationException("Fix me somehow?");
		}

		return new TinyRemapperService.Spec(
				from,
				to,
				remapJarTask.getName(),
				KotlinClasspathService.createIfRequired(project),
				isolationValue,
				MappingsService.createDefault(project, from, to),
				legacyMixin ? gradleMixinMappingProvider(project.getGradle(), extension.getMappingConfiguration().mappingsIdentifier, from, to) : null,
				remapJarTask.getClasspath().getFiles().stream().filter(File::exists).map(File::getAbsolutePath).toList()
		);
	}

	// Add all of the mixin mappings from all loom projects.
	private static List<MappingsService.Spec> gradleMixinMappingProvider(Gradle gradle, String mappingId, String from, String to) {
		var mappingSpecs = new ArrayList<MappingsService.Spec>();

		GradleUtils.allLoomProjects(gradle, project -> {
			final LoomGradleExtension extension = LoomGradleExtension.get(project);

			if (!mappingId.equals(extension.getMappingConfiguration().mappingsIdentifier)) {
				// Only find mixin mappings that are from other projects with the same mapping id.
				return;
			}

			for (SourceSet sourceSet : SourceSetHelper.getSourceSets(project)) {
				final File mixinMappings = AnnotationProcessorInvoker.getMixinMappingsForSourceSet(project, sourceSet);

				if (!mixinMappings.exists()) {
					continue;
				}

				mappingSpecs.add(MappingsService.create(mixinMappings.toPath(), from, to, false));
			}
		});

		return mappingSpecs;
	}

	private TinyRemapper tinyRemapper;
	@Nullable
	private KotlinRemapperClassloader kotlinRemapperClassloader;
	private final Map<String, InputTag> inputTagMap = new HashMap<>();

	// Set to true once remapping has started, once set no inputs can be read.
	private boolean isRemapping = false;

	private TinyRemapperService(List<IMappingProvider> mappings, boolean useMixinExtension, @Nullable KotlinClasspath kotlinClasspath, List<Path> classpath) {
		TinyRemapper.Builder builder = TinyRemapper.newRemapper();

		for (IMappingProvider provider : mappings) {
			builder.withMappings(provider);
		}

		if (useMixinExtension) {
			builder.extension(new net.fabricmc.tinyremapper.extension.mixin.MixinExtension());
		}

		if (kotlinClasspath != null) {
			kotlinRemapperClassloader = KotlinRemapperClassloader.create(kotlinClasspath);
			builder.extension(kotlinRemapperClassloader.getTinyRemapperExtension());
		}

		tinyRemapper = builder.build();
		tinyRemapper.readClassPath(classpath.toArray(Path[]::new));
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

	@Override
	public void close() throws IOException {
		if (tinyRemapper != null) {
			tinyRemapper.getEnvironment();
			tinyRemapper.finish();
			tinyRemapper = null;
		}

		if (kotlinRemapperClassloader != null) {
			kotlinRemapperClassloader.close();
		}
	}
}
