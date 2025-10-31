/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2024-2025 FabricMC
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
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Objects;

import org.gradle.api.IllegalDependencyNotation;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.commons.Remapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.providers.mappings.LayeredMappingSpecBuilderImpl;
import net.fabricmc.loom.configuration.providers.mappings.LayeredMappingsFactory;
import net.fabricmc.loom.configuration.providers.mappings.TinyMappingsService;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.TinyRemapperLoggerAdapter;
import net.fabricmc.loom.util.service.Service;
import net.fabricmc.loom.util.service.ServiceFactory;
import net.fabricmc.loom.util.service.ServiceType;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.TinyRemapper;

public final class MigrateMappingsService extends Service<MigrateMappingsService.Options> implements Closeable {
	private static final Logger LOGGER = LoggerFactory.getLogger(MigrateMappingsService.class);
	private static final ServiceType<Options, MigrateMappingsService> TYPE = new ServiceType<>(Options.class, MigrateMappingsService.class);

	private @Nullable TinyRemapper tinyRemapper;

	public MigrateMappingsService(Options options, ServiceFactory serviceFactory) {
		super(options, serviceFactory);
	}

	public interface Options extends Service.Options {
		@Nested
		Property<MappingsService.Options> getSourceMappings();
		@Nested
		Property<TinyMappingsService.Options> getTargetMappings();
		@InputFiles
		ConfigurableFileCollection getClasspath();
		@InputFiles
		ConfigurableFileCollection getMinecraftLibraryClasspath();
	}

	public static Provider<Options> createOptions(Project project, Provider<String> targetMappings) {
		LoomGradleExtension extension = LoomGradleExtension.get(project);
		final Provider<String> from = project.provider(() -> "intermediary");
		final Provider<String> to = project.provider(() -> "named");

		ConfigurableFileCollection classpath = project.getObjects().fileCollection();
		classpath.from(project.getConfigurations().getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME));
		// Question: why are both of these needed?
		classpath.from(extension.getMinecraftJars(MappingsNamespace.INTERMEDIARY));
		classpath.from(extension.getMinecraftJars(MappingsNamespace.NAMED));

		ConfigurableFileCollection minecraftLibraryClasspath = project.getObjects().fileCollection();
		minecraftLibraryClasspath.from(project.getConfigurations().getByName(Constants.Configurations.MINECRAFT_COMPILE_LIBRARIES));
		minecraftLibraryClasspath.from(project.getConfigurations().getByName(Constants.Configurations.MINECRAFT_RUNTIME_LIBRARIES));

		return TYPE.create(project, (o) -> {
			FileCollection targetMappingsFile = getTargetMappingsFile(project, targetMappings.get());
			o.getSourceMappings().set(MappingsService.createOptionsWithProjectMappings(project, from, to));
			o.getTargetMappings().set(TinyMappingsService.createOptions(project, targetMappingsFile, "mappings/mappings.tiny"));
			o.getClasspath().from(classpath);
			o.getMinecraftLibraryClasspath().from(minecraftLibraryClasspath);
		});
	}

	public MappingsService getSourceMappingsService() {
		return getServiceFactory().get(getOptions().getSourceMappings().get());
	}

	public TinyMappingsService getTargetMappingsService() {
		return getServiceFactory().get(getOptions().getTargetMappings().get());
	}

	public FileCollection getClasspath() {
		return getOptions().getClasspath();
	}

	public Remapper getRemapper() {
		if (tinyRemapper != null) {
			return tinyRemapper.getEnvironment().getRemapper();
		}

		final Path[] classpath = getClasspath().minus(getOptions().getMinecraftLibraryClasspath())
				.getFiles()
				.stream()
				.map(File::toPath)
				.distinct()
				.toArray(Path[]::new);
		tinyRemapper = TinyRemapper.newRemapper(TinyRemapperLoggerAdapter.INSTANCE)
				.withMappings(this::provideMappings)
				.build();
		tinyRemapper.readClassPath(classpath);
		return tinyRemapper.getEnvironment().getRemapper();
	}

	private void provideMappings(IMappingProvider.MappingAcceptor mappingAcceptor) {
		final MappingTree sourceTree = getSourceMappingsService().getMemoryMappingTree();
		final MappingTree targetTree = getTargetMappingsService().getMappingTree();
		final int targetIntermediaryId = targetTree.getNamespaceId(MappingsNamespace.INTERMEDIARY.toString());

		for (MappingTree.ClassMapping sourceClass : sourceTree.getClasses()) {
			final String classIntermediary = sourceClass.getName(MappingsNamespace.INTERMEDIARY.toString());
			final MappingTree.ClassMapping targetClass = targetTree.getClass(classIntermediary, targetIntermediaryId);

			if (targetClass == null) {
				continue;
			}

			final String sourceClassName = Objects.requireNonNullElse(sourceClass.getName(MappingsNamespace.NAMED.toString()), classIntermediary);
			final String targetClassName = Objects.requireNonNullElse(targetClass.getName(MappingsNamespace.NAMED.toString()), classIntermediary);
			mappingAcceptor.acceptClass(sourceClassName, targetClassName);

			for (MappingTree.FieldMapping sourceField : sourceClass.getFields()) {
				final String fieldIntermediary = sourceField.getName(MappingsNamespace.INTERMEDIARY.toString());
				final String fieldIntermediaryDesc = sourceField.getDesc(MappingsNamespace.INTERMEDIARY.toString());
				final MappingTree.FieldMapping targetField = targetTree.getField(classIntermediary, fieldIntermediary, fieldIntermediaryDesc, targetIntermediaryId);

				if (targetField != null) {
					final String sourceName = Objects.requireNonNullElse(sourceField.getName(MappingsNamespace.NAMED.toString()), fieldIntermediary);
					final String sourceDesc = sourceField.getDesc(MappingsNamespace.NAMED.toString());
					final String targetName = Objects.requireNonNullElse(targetField.getName(MappingsNamespace.NAMED.toString()), fieldIntermediary);
					mappingAcceptor.acceptField(new IMappingProvider.Member(sourceClassName, sourceName, sourceDesc), targetName);
				}
			}

			for (MappingTree.MethodMapping sourceMethod : sourceClass.getMethods()) {
				final String methodIntermediary = sourceMethod.getName(MappingsNamespace.INTERMEDIARY.toString());
				final String methodIntermediaryDesc = sourceMethod.getDesc(MappingsNamespace.INTERMEDIARY.toString());
				final MappingTree.FieldMapping targetMethod = targetTree.getField(classIntermediary, methodIntermediary, methodIntermediaryDesc, targetIntermediaryId);

				if (targetMethod != null) {
					final String sourceName = Objects.requireNonNullElse(sourceMethod.getName(MappingsNamespace.NAMED.toString()), methodIntermediary);
					final String sourceDesc = sourceMethod.getDesc(MappingsNamespace.NAMED.toString());
					final String targetName = Objects.requireNonNullElse(targetMethod.getName(MappingsNamespace.NAMED.toString()), methodIntermediary);
					mappingAcceptor.acceptMethod(new IMappingProvider.Member(sourceClassName, sourceName, sourceDesc), targetName);
				}
			}
		}
	}

	@Override
	public void close() throws IOException {
		if (tinyRemapper != null) {
			tinyRemapper.finish();
			tinyRemapper = null;
		}
	}

	/**
	 * Return a mappings file for the requested mappings.
	 */
	private static FileCollection getTargetMappingsFile(Project project, String mappings) {
		if (mappings == null || mappings.isEmpty()) {
			throw new IllegalArgumentException("No mappings were specified. Use --mappings=\"\" to specify target mappings");
		}

		try {
			if (mappings.startsWith("net.minecraft:mappings:")) {
				if (!mappings.endsWith(":" + LoomGradleExtension.get(project).getMinecraftProvider().minecraftVersion())) {
					throw new UnsupportedOperationException("Migrating Mojang mappings is currently only supported for the specified minecraft version");
				}

				LayeredMappingsFactory dep = new LayeredMappingsFactory(LayeredMappingSpecBuilderImpl.buildOfficialMojangMappings());
				return project.files(dep.resolve(project).toFile());
			} else {
				Dependency dependency = project.getDependencies().create(mappings);
				return project.getConfigurations().detachedConfiguration(dependency);
			}
		} catch (IllegalDependencyNotation ignored) {
			LOGGER.info("Could not locate mappings, presuming V2 Yarn");
			String mavenNotation = "net.fabricmc:yarn:%s:v2".formatted(mappings);
			return project.getConfigurations().detachedConfiguration(project.getDependencies().create(mavenNotation));
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to resolve mappings", e);
		}
	}
}
