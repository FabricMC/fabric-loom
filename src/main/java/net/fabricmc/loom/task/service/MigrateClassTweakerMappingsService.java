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

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Nested;
import org.objectweb.asm.commons.Remapper;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
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

public final class MigrateClassTweakerMappingsService extends Service<MigrateClassTweakerMappingsService.Options> implements Closeable {
	private static final ServiceType<Options, MigrateClassTweakerMappingsService> TYPE = new ServiceType<>(Options.class, MigrateClassTweakerMappingsService.class);
	private static final String MIGRATION_TARGET_NS = "migrationTarget";

	public MigrateClassTweakerMappingsService(Options options, ServiceFactory serviceFactory) {
		super(options, serviceFactory);
	}

	public interface Options extends Service.Options {
		@Nested
		Property<MigrateMappingsService.Options> getMappings();
		@Nested
		Property<TinyRemapperService.Options> getTinyRemapperOptions();
		@InputFile
		RegularFileProperty getMergedMappings();
	}

	public static Provider<Options> createOptions(Project project, Provider<String> targetMappings) {
		ConfigurableFileCollection minecraftLibraryClasspath = project.getObjects().fileCollection();
		minecraftLibraryClasspath.from(project.getConfigurations().getByName(Constants.Configurations.MINECRAFT_COMPILE_LIBRARIES));
		minecraftLibraryClasspath.from(project.getConfigurations().getByName(Constants.Configurations.MINECRAFT_RUNTIME_LIBRARIES));

		return TYPE.create(project, (o) -> {
			o.getMappings().set(MigrateMappingsService.createOptions(project, targetMappings));
			Provider<RegularFile> mergedMappings = o.getMappings().flatMap(m -> {
				return createMergedMappingFile(project, targetMappings, m.getSourceMappings(), m.getTargetMappings());
			});
			o.getMergedMappings().set(mergedMappings);

			o.getTinyRemapperOptions().set(TinyRemapperService.TYPE.create(project, o2 -> {
				o2.getClasspath().from(o.getMappings().map(m -> m.getClasspath().minus(minecraftLibraryClasspath)));
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
		});
	}

	public Remapper getRemapper() {
		final TinyRemapperService service = getServiceFactory().get(getOptions().getTinyRemapperOptions());
		return service.getTinyRemapperForRemapping().getEnvironment().getRemapper();
	}

	private static Provider<RegularFile> createMergedMappingFile(Project project, Provider<String> targetMappingsId, Provider<MappingsService.Options> sourceOptions, Provider<TinyMappingsService.Options> targetOptions) {
		return sourceOptions.flatMap(sourceOpt -> targetOptions.flatMap(targetOpt -> {
			final Provider<RegularFile> fileProvider = project.getLayout()
					.getBuildDirectory()
					.file(targetMappingsId.map(id -> "migrate-class-tweaker-mappings-" + Checksum.of(id).sha256().hex(16) + ".tiny"));
			return fileProvider.map(file -> {
				final Path path = file.getAsFile().toPath();

				if (!Files.exists(path) || LoomGradleExtension.get(project).refreshDeps()) {
					try {
						final MemoryMappingTree tree = mergeMappings(sourceOpt, targetOpt);
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
		}));
	}

	private static MemoryMappingTree mergeMappings(MappingsService.Options sourceOptions, TinyMappingsService.Options targetOptions) throws IOException {
		final var tree = new MemoryMappingTree();
		MappingReader.read(sourceOptions.getMappingsFile().get().getAsFile().toPath(), tree);
		final var renamer = new MappingNsRenamer(tree, Map.of(MappingsNamespace.NAMED.toString(), MIGRATION_TARGET_NS));
		final Path mappingFile = targetOptions.getMappings().getSingleFile().toPath();

		if (targetOptions.getZipEntryPath().isPresent()) {
			try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(mappingFile)) {
				MappingReader.read(fs.getPath(targetOptions.getZipEntryPath().get()), renamer);
			}
		} else {
			MappingReader.read(mappingFile, renamer);
		}

		return tree;
	}

	@Override
	public void close() throws IOException {
		Files.deleteIfExists(getOptions().getMergedMappings().get().getAsFile().toPath());
	}
}
