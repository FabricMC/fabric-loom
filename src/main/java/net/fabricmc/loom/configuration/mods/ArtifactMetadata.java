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

public record ArtifactMetadata(boolean isFabricMod, RemapRequirements remapRequirements, @Nullable InstallerData installerData) {
	private static final String INSTALLER_PATH = "fabric-installer.json";
	private static final String MANIFEST_PATH = "META-INF/MANIFEST.MF";
	private static final String MANIFEST_REMAP_KEY = "Fabric-Loom-Remap";

	public static ArtifactMetadata create(ArtifactRef artifact) throws IOException {
		boolean isFabricMod;
		RemapRequirements remapRequirements = RemapRequirements.DEFAULT;
		InstallerData installerData = null;

		try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(artifact.path())) {
			isFabricMod = FabricModJsonFactory.containsMod(fs);
			final Path manifestPath = fs.getPath(MANIFEST_PATH);

			if (Files.exists(manifestPath)) {
				final var manifest = new Manifest(new ByteArrayInputStream(Files.readAllBytes(manifestPath)));
				final Attributes mainAttributes = manifest.getMainAttributes();
				final String value = mainAttributes.getValue(MANIFEST_REMAP_KEY);

				if (value != null) {
					// Support opting into and out of remapping with "Fabric-Loom-Remap" manifest entry
					remapRequirements = Boolean.parseBoolean(value) ? RemapRequirements.OPT_IN : RemapRequirements.OPT_OUT;
				}
			}

			final Path installerPath = fs.getPath(INSTALLER_PATH);

			if (isFabricMod && Files.exists(installerPath)) {
				final JsonObject jsonObject = LoomGradlePlugin.GSON.fromJson(Files.readString(installerPath, StandardCharsets.UTF_8), JsonObject.class);
				installerData = new InstallerData(artifact, jsonObject);
			}
		}

		return new ArtifactMetadata(isFabricMod, remapRequirements, installerData);
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
