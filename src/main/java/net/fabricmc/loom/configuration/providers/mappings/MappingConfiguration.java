/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2026 FabricMC
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
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.configuration.DependencyInfo;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.providers.mappings.extras.annotations.AnnotationsData;
import net.fabricmc.loom.configuration.providers.mappings.extras.annotations.AnnotationsLayer;
import net.fabricmc.loom.configuration.providers.mappings.tiny.TinyJarInfo;
import net.fabricmc.loom.configuration.providers.mappings.unpick.UnpickMetadata;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftProvider;
import net.fabricmc.loom.api.decompilers.JavadocStyle;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.FileSystemUtil;
import net.fabricmc.loom.util.service.ServiceFactory;

public abstract sealed class MappingConfiguration permits RemapMappingConfiguration, NoRemapMappingConfiguration {
	protected static final Logger LOGGER = LoggerFactory.getLogger(MappingConfiguration.class);

	public final String mappingsIdentifier;

	private final Path inputJar;
	@Nullable
	private byte[] unpickDefinitions;

	private List<AnnotationsData> annotationsData = List.of();
	@Nullable
	private UnpickMetadata unpickMetadata;

	protected MappingConfiguration(String mappingsIdentifier, Path inputJar) {
		this.mappingsIdentifier = mappingsIdentifier;
		this.inputJar = inputJar;
	}

	protected final void setup(Project project, MinecraftProvider minecraftProvider, DependencyInfo dependency, String displayName) {
		try {
			prepare(minecraftProvider);

			try (FileSystemUtil.Delegate delegate = FileSystemUtil.getJarFileSystem(inputJar)) {
				readExtras(delegate.fs());
				setupMappings(project, minecraftProvider, delegate.fs());
			}
		} catch (IOException e) {
			try {
				cleanup();
			} catch (IOException cleanupException) {
				e.addSuppressed(cleanupException);
			}

			throw new UncheckedIOException("Failed to setup %s: %s".formatted(displayName, dependency.getDepString()), e);
		}
	}

	protected void prepare(MinecraftProvider minecraftProvider) throws IOException {
	}

	protected void cleanup() throws IOException {
	}

	protected abstract void setupMappings(Project project, MinecraftProvider minecraftProvider, FileSystem inputJar) throws IOException;

	public abstract Provider<TinyMappingsService.Options> getMappingsServiceOptions(Project project);

	public abstract String getMappingsHash();

	public abstract MappingsNamespace getRuntimeNamespace();

	public abstract JavadocStyle getJavadocStyle();

	public TinyMappingsService getMappingsService(Project project, ServiceFactory serviceFactory) {
		return serviceFactory.get(getMappingsServiceOptions(project));
	}

	public void applyToProject(Project project, DependencyInfo dependency) {
		if (unpickMetadata != null && unpickMetadata.hasConstants()) {
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

	protected static Path resolveInputJar(DependencyInfo dependency, String displayName) {
		return dependency.resolveFile()
				.orElseThrow(() -> new RuntimeException("Could not resolve %s: %s".formatted(displayName, dependency)))
				.toPath();
	}

	protected static TinyJarInfo readJarInfo(Path inputJar, DependencyInfo dependency, MinecraftProvider minecraftProvider, String displayName) {
		TinyJarInfo jarInfo = TinyJarInfo.get(inputJar);
		jarInfo.minecraftVersionId().ifPresent(id -> {
			if (!minecraftProvider.minecraftVersion().equals(id)) {
				LOGGER.warn("The %s (%s) were not built for Minecraft version %s, proceed with caution.".formatted(displayName, dependency.getDepString(), minecraftProvider.minecraftVersion()));
			}
		});
		return jarInfo;
	}

	protected static String getMappingsClassifier(DependencyInfo dependency, boolean isV2) {
		String[] depStringSplit = dependency.getDepString().split(":");

		if (depStringSplit.length >= 4) {
			return "-" + depStringSplit[3] + (isV2 ? "-v2" : "");
		}

		return isV2 ? "-v2" : "";
	}

	protected static String createMappingsIdentifier(String mappingsName, String version, String classifier, String minecraftVersion) {
		//          mappingsName      . mcVersion . version        classifier
		// Example: net.fabricmc.yarn . 1_16_5    . 1.16.5+build.5 -v2
		return mappingsName + "." + minecraftVersion.replace(' ', '_').replace('.', '_').replace('-', '_') + "." + version + classifier;
	}

	private void readExtras(FileSystem jar) throws IOException {
		readAnnotationsData(jar);
		readUnpickDefinitions(jar);
	}

	private void readAnnotationsData(FileSystem jar) throws IOException {
		Path annotationsPath = jar.getPath(AnnotationsLayer.ANNOTATIONS_PATH);

		if (!Files.exists(annotationsPath)) {
			return;
		}

		try (BufferedReader reader = Files.newBufferedReader(annotationsPath, StandardCharsets.UTF_8)) {
			annotationsData = AnnotationsData.readList(reader);
		}
	}

	private void readUnpickDefinitions(FileSystem jar) throws IOException {
		Path unpickPath = jar.getPath(UnpickMetadata.UNPICK_DEFINITIONS_PATH);
		Path unpickMetadataPath = jar.getPath(UnpickMetadata.UNPICK_METADATA_PATH);

		if (!Files.exists(unpickPath) || !Files.exists(unpickMetadataPath)) {
			return;
		}

		unpickMetadata = UnpickMetadata.parse(unpickMetadataPath);
		unpickDefinitions = Files.readAllBytes(unpickPath);
	}

	protected final Path inputJar() {
		return inputJar;
	}

	public String mappingsIdentifier() {
		return mappingsIdentifier;
	}

	public byte[] getUnpickDefinitions() {
		return Objects.requireNonNull(unpickDefinitions, "Unpick definitions are not available");
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
}
