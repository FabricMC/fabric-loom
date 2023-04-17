/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022-2023 FabricMC
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

package net.fabricmc.loom.configuration.mods;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.configuration.InstallerData;
import net.fabricmc.loom.util.FileSystemUtil;
import net.fabricmc.loom.util.fmj.FabricModJsonFactory;

public record ArtifactMetadata(boolean isFabricMod, RemapRequirements remapRequirements, @Nullable InstallerData installerData, boolean isAnnotationProcessor) {
	private static final String INSTALLER_PATH = "fabric-installer.json";
	private static final String MANIFEST_PATH = "META-INF/MANIFEST.MF";
	private static final String MANIFEST_REMAP_KEY = "Fabric-Loom-Remap";
	private static final String MANIFEST_ANNOTATION_PROCESSOR_KEY = "Fabric-Loom-Annotation-Processor";

	public static ArtifactMetadata create(ArtifactRef artifact) throws IOException {
		boolean isFabricMod;
		RemapRequirements remapRequirements = RemapRequirements.DEFAULT;
		InstallerData installerData = null;
		boolean isAnnotationProcessor = false;

		try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(artifact.path())) {
			isFabricMod = FabricModJsonFactory.containsMod(fs);
			final Path manifestPath = fs.getPath(MANIFEST_PATH);

			if (Files.exists(manifestPath)) {
				final var manifest = new Manifest(new ByteArrayInputStream(Files.readAllBytes(manifestPath)));
				final Attributes mainAttributes = manifest.getMainAttributes();
				final String remapValue = mainAttributes.getValue(MANIFEST_REMAP_KEY);
				final String annotationProcessorValue = mainAttributes.getValue(MANIFEST_ANNOTATION_PROCESSOR_KEY);

				if (remapValue != null) {
					// Support opting into and out of remapping with "Fabric-Loom-Remap" manifest entry
					remapRequirements = Boolean.parseBoolean(remapValue) ? RemapRequirements.OPT_IN : RemapRequirements.OPT_OUT;
				}

				if (annotationProcessorValue != null) {
					// Allow mods to add themselves onto the annotation processor classpath.
					isAnnotationProcessor = Boolean.parseBoolean(annotationProcessorValue);
				}
			}

			final Path installerPath = fs.getPath(INSTALLER_PATH);

			if (isFabricMod && Files.exists(installerPath)) {
				final JsonObject jsonObject = LoomGradlePlugin.GSON.fromJson(Files.readString(installerPath, StandardCharsets.UTF_8), JsonObject.class);
				installerData = new InstallerData(artifact.version(), jsonObject);
			}
		}

		if (remapRequirements != RemapRequirements.OPT_OUT && isAnnotationProcessor) {
			throw new UnsupportedOperationException("Annotation processor artifact (%s) must opt out of remapping with the (%s) manifest key.".formatted(artifact.name(), MANIFEST_REMAP_KEY));
		}

		return new ArtifactMetadata(isFabricMod, remapRequirements, installerData, isAnnotationProcessor);
	}

	public boolean shouldRemap() {
		return remapRequirements().getShouldRemap().test(this);
	}

	public enum RemapRequirements {
		DEFAULT(ArtifactMetadata::isFabricMod),
		OPT_IN(true),
		OPT_OUT(false);

		private final Predicate<ArtifactMetadata> shouldRemap;

		RemapRequirements(Predicate<ArtifactMetadata> shouldRemap) {
			this.shouldRemap = shouldRemap;
		}

		RemapRequirements(final boolean shouldRemap) {
			this.shouldRemap = artifactMetadata -> shouldRemap;
		}

		private Predicate<ArtifactMetadata> getShouldRemap() {
			return shouldRemap;
		}
	}
}
