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

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.gradle.api.Project;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.SourceSet;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.build.mixin.AnnotationProcessorInvoker;
import net.fabricmc.loom.task.AbstractRemapJarTask;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.gradle.GradleUtils;
import net.fabricmc.loom.util.gradle.SourceSetHelper;
import net.fabricmc.loom.util.kotlin.KotlinClasspathService;
import net.fabricmc.loom.util.kotlin.KotlinRemapperClassloader;
import net.fabricmc.loom.util.service.Service;
import net.fabricmc.loom.util.service.ServiceFactory;
import net.fabricmc.loom.util.service.ServiceType;
import net.fabricmc.tinyremapper.InputTag;
import net.fabricmc.tinyremapper.TinyRemapper;

public class TinyRemapperService extends Service<TinyRemapperService.Options> implements Closeable {
	public static final ServiceType<Options, TinyRemapperService> TYPE = new ServiceType<>(Options.class, TinyRemapperService.class);

	public interface Options extends Service.Options {
		@Input
		Property<String> getFrom();
		@Input
		Property<String> getTo();
		@Nested
		ListProperty<MappingsService.Options> getMappings();
		@Input
		Property<Boolean> getUselegacyMixinAP();
		@Nested
		@Optional
		Property<KotlinClasspathService.Options> getKotlinClasspathService();
		@InputFiles
		ConfigurableFileCollection getClasspath();
		@Input
		ListProperty<String> getKnownIndyBsms();

		// TODO Remapper extensions somehow
	}

	public static Provider<Options> createOptions(AbstractRemapJarTask remapJarTask) {
		final Project project = remapJarTask.getProject();
		final LoomGradleExtension extension = LoomGradleExtension.get(project);
		final boolean legacyMixin = extension.getMixin().getUseLegacyMixinAp().get();
		final ConfigurationContainer configurations = project.getConfigurations();
		final FileCollection classpath = remapJarTask.getClasspath()
				.minus(configurations.getByName(Constants.Configurations.MINECRAFT_COMPILE_LIBRARIES))
				.minus(configurations.getByName(Constants.Configurations.MINECRAFT_RUNTIME_LIBRARIES));

		return TYPE.create(project, options -> {
			options.getFrom().set(remapJarTask.getSourceNamespace());
			options.getTo().set(remapJarTask.getTargetNamespace());
			// TODO pass the namespaces through as providers?
			options.getMappings().add(MappingsService.createOptionsWithProjectMappings(project, options.getFrom().get(), options.getTo().get()));

			if (legacyMixin) {
				options.getMappings().addAll(project.provider(() -> getLegacyMixinMappings(project, options.getFrom().get(), options.getTo().get())));
			}

			options.getUselegacyMixinAP().set(legacyMixin);
			options.getKotlinClasspathService().set(KotlinClasspathService.createOptions(project));
			options.getClasspath().from(classpath);
			options.getKnownIndyBsms().set(extension.getKnownIndyBsms());
		});
	}

	private static List<MappingsService.Options> getLegacyMixinMappings(Project thisProject, String from, String to) {
		final LoomGradleExtension thisExtension = LoomGradleExtension.get(thisProject);
		String mappingId = thisExtension.getMappingConfiguration().mappingsIdentifier;

		var mappings = new ArrayList<Provider<MappingsService.Options>>();

		GradleUtils.allLoomProjects(thisProject.getGradle(), project -> {
			final LoomGradleExtension extension = LoomGradleExtension.get(project);

			if (!mappingId.equals(extension.getMappingConfiguration().mappingsIdentifier)) {
				// Only find mixin mappings that are from other projects with the same mapping id.
				return;
			}

			for (SourceSet sourceSet : SourceSetHelper.getSourceSets(project)) {
				final File mixinMappings = AnnotationProcessorInvoker.getMixinMappingsForSourceSet(project, sourceSet);

				// TODO this is likely too early now, maybe allow mapping services to do nothing in the mappings dont exist?
				if (!mixinMappings.exists()) {
					continue;
				}

				mappings.add(MappingsService.createOptions(thisProject, mixinMappings.toPath(), from, to, false));
			}
		});

		return mappings.stream().map(Provider::get).toList();
	}

	private TinyRemapper tinyRemapper;
	@Nullable
	private KotlinRemapperClassloader kotlinRemapperClassloader;
	private final Map<String, InputTag> inputTagMap = new HashMap<>();
	private final HashSet<Path> classpath = new HashSet<>();
	// Set to true once remapping has started, once set no inputs can be read.
	private boolean isRemapping = false;

	public TinyRemapperService(Options options, ServiceFactory serviceFactory) {
		super(options, serviceFactory);
		tinyRemapper = createTinyRemapper();
	}

	private TinyRemapper createTinyRemapper() {
		TinyRemapper.Builder builder = TinyRemapper.newRemapper()
				.withKnownIndyBsm(Set.copyOf(getOptions().getKnownIndyBsms().get()));

		for (MappingsService.Options options : getOptions().getMappings().get()) {
			MappingsService mappingsService = getServiceFactory().get(options);
			builder.withMappings(mappingsService.getMappingsProvider());
		}

		if (!getOptions().getUselegacyMixinAP().get()) {
			builder.extension(new net.fabricmc.tinyremapper.extension.mixin.MixinExtension());
		}

		if (getOptions().getKotlinClasspathService().isPresent()) {
			KotlinClasspathService kotlinClasspathService = getServiceFactory().get(getOptions().getKotlinClasspathService());
			kotlinRemapperClassloader = KotlinRemapperClassloader.create(kotlinClasspathService);
			builder.extension(kotlinRemapperClassloader.getTinyRemapperExtension());
		}

		// TODO fix me
		// for (RemapperExtensionHolder holder : remapperExtensions) {
		//     holder.apply(builder, sourceNamespace, targetNamespace, objectFactory);
		// }

		return builder.build();
	}

	public InputTag getOrCreateTag(Path file) {
		InputTag tag = inputTagMap.get(file.toAbsolutePath().toString());

		if (tag == null) {
			tag = tinyRemapper.createInputTag();
			inputTagMap.put(file.toAbsolutePath().toString(), tag);
		}

		return tag;
	}

	public TinyRemapper getTinyRemapperForRemapping() {
		isRemapping = true;
		return Objects.requireNonNull(tinyRemapper, "Tiny remapper has not been setup");
	}

	public TinyRemapper getTinyRemapperForInputs() {
		if (isRemapping) {
			throw new IllegalStateException("Cannot read inputs as remapping has already started");
		}

		return tinyRemapper;
	}

	void readClasspath(List<Path> paths) {
		List<Path> toRead = new ArrayList<>();

		for (Path path: paths) {
			if (classpath.contains(path)) {
				continue;
			}

			toRead.add(path);
			classpath.add(path);
		}

		if (toRead.isEmpty()) {
			return;
		}

		tinyRemapper.readClassPath(toRead.toArray(Path[]::new));
	}

	@Override
	public void close() throws IOException {
		if (tinyRemapper != null) {
			tinyRemapper.finish();
			tinyRemapper = null;
		}

		if (kotlinRemapperClassloader != null) {
			kotlinRemapperClassloader.close();
		}
	}
}
