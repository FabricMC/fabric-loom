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
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.gradle.api.IllegalDependencyNotation;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.objectweb.asm.commons.Remapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.providers.mappings.LayeredMappingSpecBuilderImpl;
import net.fabricmc.loom.configuration.providers.mappings.LayeredMappingsFactory;
import net.fabricmc.loom.configuration.providers.mappings.TinyMappingsService;
import net.fabricmc.loom.util.Checksum;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.FileSystemUtil;
import net.fabricmc.loom.util.service.Service;
import net.fabricmc.loom.util.service.ServiceFactory;
import net.fabricmc.loom.util.service.ServiceType;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.adapter.MappingNsRenamer;
import net.fabricmc.mappingio.format.tiny.Tiny2FileWriter;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

public final class MigrateMappingsService extends Service<MigrateMappingsService.Options> implements Closeable {
	private static final Logger LOGGER = LoggerFactory.getLogger(MigrateMappingsService.class);
	private static final ServiceType<Options, MigrateMappingsService> TYPE = new ServiceType<>(Options.class, MigrateMappingsService.class);
	private static final String MIGRATION_TARGET_NS = "migrationTarget";

	public MigrateMappingsService(Options options, ServiceFactory serviceFactory) {
		super(options, serviceFactory);
	}

	public interface Options extends Service.Options {
		@Nested
		Property<MappingsService.Options> getSourceMappings();
		@Nested
		Property<TinyMappingsService.Options> getTargetMappings();
		@Nested
		Property<TinyRemapperService.Options> getTinyRemapperOptions();
		@InputFiles
		ConfigurableFileCollection getClasspath();
		@InputFiles
		ConfigurableFileCollection getMinecraftLibraryClasspath();
		@InputFile
		RegularFileProperty getMergedMappings();
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
			Provider<RegularFile> mergedMappings = createMergedMappingFile(project, targetMappings, o.getSourceMappings(), targetMappingsFile);
			o.getMergedMappings().set(mergedMappings);

			o.getTinyRemapperOptions().set(TinyRemapperService.TYPE.create(project, o2 -> {
				o2.getClasspath().from(classpath.minus(minecraftLibraryClasspath));
				o2.getFrom().set(MappingsNamespace.NAMED.toString());
				o2.getTo().set(MIGRATION_TARGET_NS);
				o2.getMappings().add(MappingsService.TYPE.create(project, o3 -> {
					o3.getMappingsFile().set(mergedMappings);
					o3.getFrom().set(MappingsNamespace.NAMED.toString());
					o3.getTo().set(MIGRATION_TARGET_NS);
					o3.getRemapLocals().set(false);
				}));
				o2.getUselegacyMixinAP().set(false);
			}));

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
		final TinyRemapperService service = getServiceFactory().get(getOptions().getTinyRemapperOptions());
		return service.getTinyRemapperForRemapping().getEnvironment().getRemapper();
	}

	private static Provider<RegularFile> createMergedMappingFile(Project project, Provider<String> targetMappingsId, Provider<MappingsService.Options> sourceOptions, FileCollection targetMappings) {
		return sourceOptions.flatMap(sourceOpt -> {
			final Provider<RegularFile> fileProvider = project.getLayout()
					.getBuildDirectory()
					.file(targetMappingsId.map(id -> "migrate-mappings-" + Checksum.of(id).sha256().hex(16) + ".tiny"));
			return fileProvider.map(file -> {
				final Path path = file.getAsFile().toPath();

				if (!Files.exists(path) || LoomGradleExtension.get(project).refreshDeps()) {
					try {
						final MemoryMappingTree tree = mergeMappings(sourceOpt, targetMappings);
						Files.createDirectories(path.getParent());

						try (var writer = new Tiny2FileWriter(Files.newBufferedWriter(path, StandardCharsets.UTF_8), false)) {
							tree.accept(writer);
						}
					} catch (IOException e) {
						throw new UncheckedIOException(e);
					}
				}

				return file;
			});
		});
	}

	private static MemoryMappingTree mergeMappings(MappingsService.Options sourceOptions, FileCollection targetFiles) throws IOException {
		final var tree = new MemoryMappingTree();
		MappingReader.read(sourceOptions.getMappingsFile().get().getAsFile().toPath(), tree);

		try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(targetFiles.getSingleFile().toPath())) {
			final var renamer = new MappingNsRenamer(tree, Map.of(MappingsNamespace.NAMED.toString(), MIGRATION_TARGET_NS));
			MappingReader.read(fs.getPath("mappings/mappings.tiny"), renamer);
		}

		return tree;
	}

	@Override
	public void close() throws IOException {
		Files.deleteIfExists(getOptions().getMergedMappings().get().getAsFile().toPath());
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
