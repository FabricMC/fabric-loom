/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2023 FabricMC
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

package net.fabricmc.loom.configuration.providers.mappings;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.tools.ant.util.StringUtils;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.configuration.DependencyInfo;
import net.fabricmc.loom.configuration.providers.mappings.extras.annotations.AnnotationsData;
import net.fabricmc.loom.configuration.providers.mappings.extras.annotations.AnnotationsLayer;
import net.fabricmc.loom.configuration.providers.mappings.tiny.MappingsMerger;
import net.fabricmc.loom.configuration.providers.mappings.tiny.TinyJarInfo;
import net.fabricmc.loom.configuration.providers.mappings.unpick.UnpickMetadata;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftProvider;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.DeletingFileVisitor;
import net.fabricmc.loom.util.FileSystemUtil;
import net.fabricmc.loom.util.ZipUtils;
import net.fabricmc.loom.util.service.ServiceFactory;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.stitch.Command;
import net.fabricmc.stitch.commands.CommandProposeFieldNames;

public class MappingConfiguration {
	private static final Logger LOGGER = LoggerFactory.getLogger(MappingConfiguration.class);

	public final String mappingsIdentifier;

	private final Path mappingsWorkingDir;
	// The mappings that gradle gives us
	private final Path baseTinyMappings;
	// The mappings we use in practice
	public final Path tinyMappings;
	public final Path tinyMappingsJar;
	private final Path unpickDefinitions;

	private List<AnnotationsData> annotationsData = List.of();
	@Nullable
	private UnpickMetadata unpickMetadata;
	private Map<String, String> signatureFixes;

	private MappingConfiguration(String mappingsIdentifier, Path mappingsWorkingDir) {
		this.mappingsIdentifier = mappingsIdentifier;

		this.mappingsWorkingDir = mappingsWorkingDir;
		this.baseTinyMappings = mappingsWorkingDir.resolve("mappings-base.tiny");
		this.tinyMappings = mappingsWorkingDir.resolve("mappings.tiny");
		this.tinyMappingsJar = mappingsWorkingDir.resolve("mappings.jar");
		this.unpickDefinitions = mappingsWorkingDir.resolve("mappings.unpick");
	}

	public static MappingConfiguration create(Project project, ServiceFactory serviceFactory, DependencyInfo dependency, MinecraftProvider minecraftProvider) {
		final String version = dependency.getResolvedVersion();
		final Path inputJar = dependency.resolveFile().orElseThrow(() -> new RuntimeException("Could not resolve mappings: " + dependency)).toPath();
		final String mappingsName = StringUtils.removeSuffix(dependency.getDependency().getGroup() + "." + dependency.getDependency().getName(), "-unmerged");

		final TinyJarInfo jarInfo = TinyJarInfo.get(inputJar);
		jarInfo.minecraftVersionId().ifPresent(id -> {
			if (!minecraftProvider.minecraftVersion().equals(id)) {
				LOGGER.warn("The mappings (%s) were not built for Minecraft version %s, proceed with caution.".formatted(dependency.getDepString(), minecraftProvider.minecraftVersion()));
			}
		});

		final String mappingsIdentifier = createMappingsIdentifier(mappingsName, version, getMappingsClassifier(dependency, jarInfo.v2()), minecraftProvider.minecraftVersion());
		final Path workingDir = minecraftProvider.dir(mappingsIdentifier).toPath();

		var mappingProvider = new MappingConfiguration(mappingsIdentifier, workingDir);

		try {
			mappingProvider.setup(project, serviceFactory, minecraftProvider, inputJar);
		} catch (IOException e) {
			cleanWorkingDirectory(workingDir);
			throw new UncheckedIOException("Failed to setup mappings: " + dependency.getDepString(), e);
		}

		return mappingProvider;
	}

	public Provider<TinyMappingsService.Options> getMappingsServiceOptions(Project project) {
		return TinyMappingsService.createOptions(project, Objects.requireNonNull(tinyMappings));
	}

	public TinyMappingsService getMappingsService(Project project, ServiceFactory serviceFactory) {
		return serviceFactory.get(getMappingsServiceOptions(project));
	}

	private void setup(Project project, ServiceFactory serviceFactory, MinecraftProvider minecraftProvider, Path inputJar) throws IOException {
		if (minecraftProvider.refreshDeps()) {
			cleanWorkingDirectory(mappingsWorkingDir);
		}

		if (Files.notExists(tinyMappings) || minecraftProvider.refreshDeps()) {
			storeMappings(project, serviceFactory, minecraftProvider, inputJar);
		} else {
			try (FileSystem fileSystem = FileSystems.newFileSystem(inputJar, (ClassLoader) null)) {
				extractExtras(fileSystem);
			}
		}

		if (Files.notExists(tinyMappingsJar) || minecraftProvider.refreshDeps()) {
			Files.deleteIfExists(tinyMappingsJar);
			ZipUtils.add(tinyMappingsJar, "mappings/mappings.tiny", Files.readAllBytes(tinyMappings));
		}
	}

	public void applyToProject(Project project, DependencyInfo dependency) {
		if (unpickMetadata != null) {
			if (unpickMetadata.hasConstants()) {
				String notation = switch (unpickMetadata) {
				case UnpickMetadata.V1 v1 -> String.format("%s:%s:%s:constants",
						dependency.getDependency().getGroup(),
						dependency.getDependency().getName(),
						dependency.getDependency().getVersion()
				);
				case UnpickMetadata.V2 v2 -> Objects.requireNonNull(v2.constants());
				};

				project.getDependencies().add(Constants.Configurations.MAPPING_CONSTANTS, notation);
			}
		}

		project.getDependencies().add(Constants.Configurations.MAPPINGS_FINAL, project.files(tinyMappingsJar.toFile()));
	}

	private static String getMappingsClassifier(DependencyInfo dependency, boolean isV2) {
		String[] depStringSplit = dependency.getDepString().split(":");

		if (depStringSplit.length >= 4) {
			return "-" + depStringSplit[3] + (isV2 ? "-v2" : "");
		}

		return isV2 ? "-v2" : "";
	}

	private void storeMappings(Project project, ServiceFactory serviceFactory, MinecraftProvider minecraftProvider, Path inputJar) throws IOException {
		LOGGER.info(":extracting " + inputJar.getFileName());

		try (FileSystemUtil.Delegate delegate = FileSystemUtil.getJarFileSystem(inputJar)) {
			extractMappings(delegate.fs(), baseTinyMappings);
			extractExtras(delegate.fs());
		}

		if (areMappingsV2(baseTinyMappings)) {
			// These are unmerged v2 mappings
			IntermediateMappingsService intermediateMappingsService = serviceFactory.get(IntermediateMappingsService.createOptions(project, minecraftProvider));

			MappingsMerger.mergeAndSaveMappings(baseTinyMappings, tinyMappings, minecraftProvider, intermediateMappingsService);
		} else {
			final List<Path> minecraftJars = minecraftProvider.getMinecraftJars();

			if (minecraftJars.size() != 1) {
				throw new UnsupportedOperationException("V1 mappings only support single jar minecraft providers");
			}

			// These are merged v1 mappings
			Files.deleteIfExists(tinyMappings);
			LOGGER.info(":populating field names");
			suggestFieldNames(minecraftJars.get(0), baseTinyMappings, tinyMappings);
		}
	}

	private static boolean areMappingsV2(Path path) throws IOException {
		try (BufferedReader reader = Files.newBufferedReader(path)) {
			return MappingReader.detectFormat(reader) == MappingFormat.TINY_2_FILE;
		}
	}

	public static void extractMappings(Path jar, Path extractTo) throws IOException {
		try (FileSystemUtil.Delegate delegate = FileSystemUtil.getJarFileSystem(jar)) {
			extractMappings(delegate.fs(), extractTo);
		}
	}

	public static void extractMappings(FileSystem jar, Path extractTo) throws IOException {
		Files.copy(jar.getPath("mappings/mappings.tiny"), extractTo, StandardCopyOption.REPLACE_EXISTING);
	}

	private void extractExtras(FileSystem jar) throws IOException {
		extractAnnotationsData(jar);
		extractUnpickDefinitions(jar);
		extractSignatureFixes(jar);
	}

	private void extractAnnotationsData(FileSystem jar) throws IOException {
		Path annotationsPath = jar.getPath(AnnotationsLayer.ANNOTATIONS_PATH);

		if (!Files.exists(annotationsPath)) {
			return;
		}

		try (BufferedReader reader = Files.newBufferedReader(annotationsPath, StandardCharsets.UTF_8)) {
			annotationsData = AnnotationsData.readList(reader);
		}
	}

	private void extractUnpickDefinitions(FileSystem jar) throws IOException {
		Path unpickPath = jar.getPath(UnpickMetadata.UNPICK_DEFINITIONS_PATH);
		Path unpickMetadataPath = jar.getPath(UnpickMetadata.UNPICK_METADATA_PATH);

		if (!Files.exists(unpickPath) || !Files.exists(unpickMetadataPath)) {
			return;
		}

		Files.copy(unpickPath, unpickDefinitions, StandardCopyOption.REPLACE_EXISTING);

		unpickMetadata = UnpickMetadata.parse(unpickMetadataPath);
	}

	private void extractSignatureFixes(FileSystem jar) throws IOException {
		Path recordSignaturesJsonPath = jar.getPath("extras/record_signatures.json");

		if (!Files.exists(recordSignaturesJsonPath)) {
			return;
		}

		try (Reader reader = Files.newBufferedReader(recordSignaturesJsonPath, StandardCharsets.UTF_8)) {
			//noinspection unchecked
			signatureFixes = LoomGradlePlugin.GSON.fromJson(reader, Map.class);
		}
	}

	private void suggestFieldNames(Path inputJar, Path oldMappings, Path newMappings) {
		Command command = new CommandProposeFieldNames();
		runCommand(command, inputJar.toFile().getAbsolutePath(),
						oldMappings.toAbsolutePath().toString(),
						newMappings.toAbsolutePath().toString());
	}

	private void runCommand(Command command, String... args) {
		try {
			command.run(args);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static void cleanWorkingDirectory(Path mappingsWorkingDir) {
		try {
			if (Files.exists(mappingsWorkingDir)) {
				Files.walkFileTree(mappingsWorkingDir, new DeletingFileVisitor());
			}

			Files.createDirectories(mappingsWorkingDir);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public Path mappingsWorkingDir() {
		return mappingsWorkingDir;
	}

	private static String createMappingsIdentifier(String mappingsName, String version, String classifier, String minecraftVersion) {
		//          mappingsName      . mcVersion . version        classifier
		// Example: net.fabricmc.yarn . 1_16_5    . 1.16.5+build.5 -v2
		return mappingsName + "." + minecraftVersion.replace(' ', '_').replace('.', '_').replace('-', '_') + "." + version + classifier;
	}

	public String mappingsIdentifier() {
		return mappingsIdentifier;
	}

	public File getUnpickDefinitionsFile() {
		return unpickDefinitions.toFile();
	}

	public boolean hasUnpickDefinitions() {
		return unpickMetadata != null;
	}

	public List<AnnotationsData> getAnnotationsData() {
		return annotationsData;
	}

	public UnpickMetadata getUnpickMetadata() {
		return Objects.requireNonNull(unpickMetadata, "Unpick metadata is not available");
	}

	@Nullable
	public Map<String, String> getSignatureFixes() {
		return signatureFixes;
	}
}
