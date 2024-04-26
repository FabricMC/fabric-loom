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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.configuration.InstallerData;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.FileSystemUtil;
import net.fabricmc.loom.util.fmj.FabricModJsonFactory;

public record ArtifactMetadata(boolean isFabricMod, RemapRequirements remapRequirements, @Nullable InstallerData installerData, MixinRemapType mixinRemapType, List<String> knownIdyBsms) {
	private static final String INSTALLER_PATH = "fabric-installer.json";

	public static ArtifactMetadata create(ArtifactRef artifact, String currentLoomVersion) throws IOException {
		boolean isFabricMod;
		RemapRequirements remapRequirements = RemapRequirements.DEFAULT;
		InstallerData installerData = null;
		MixinRemapType refmapRemapType = MixinRemapType.MIXIN;
		List<String> knownIndyBsms = new ArrayList<>();

		try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(artifact.path())) {
			isFabricMod = FabricModJsonFactory.containsMod(fs);
			final Path manifestPath = fs.getPath(Constants.Manifest.PATH);

			if (Files.exists(manifestPath)) {
				final var manifest = new Manifest(new ByteArrayInputStream(Files.readAllBytes(manifestPath)));
				final Attributes mainAttributes = manifest.getMainAttributes();
				final String remapValue = mainAttributes.getValue(Constants.Manifest.REMAP_KEY);
				final String loomVersion = mainAttributes.getValue(Constants.Manifest.LOOM_VERSION);
				final String mixinRemapType = mainAttributes.getValue(Constants.Manifest.MIXIN_REMAP_TYPE);
				final String knownIndyBsmsValue = mainAttributes.getValue(Constants.Manifest.KNOWN_IDY_BSMS);

				if (remapValue != null) {
					// Support opting into and out of remapping with "Fabric-Loom-Remap" manifest entry
					remapRequirements = Boolean.parseBoolean(remapValue) ? RemapRequirements.OPT_IN : RemapRequirements.OPT_OUT;
				}

				if (mixinRemapType != null) {
					try {
						refmapRemapType = MixinRemapType.valueOf(mixinRemapType.toUpperCase(Locale.ROOT));
					} catch (IllegalArgumentException e) {
						throw new IllegalStateException("Unknown mixin remap type: " + mixinRemapType);
					}
				}

				if (loomVersion != null && refmapRemapType != MixinRemapType.STATIC) {
					validateLoomVersion(loomVersion, currentLoomVersion);
				}

				if (knownIndyBsmsValue != null) {
					Collections.addAll(knownIndyBsms, knownIndyBsmsValue.split(","));
				}
			}

			final Path installerPath = fs.getPath(INSTALLER_PATH);

			if (isFabricMod && Files.exists(installerPath)) {
				final JsonObject jsonObject = LoomGradlePlugin.GSON.fromJson(Files.readString(installerPath, StandardCharsets.UTF_8), JsonObject.class);
				installerData = new InstallerData(artifact.version(), jsonObject);
			}
		}

		return new ArtifactMetadata(isFabricMod, remapRequirements, installerData, refmapRemapType, Collections.unmodifiableList(knownIndyBsms));
	}

	// Validates that the version matches or is less than the current loom version
	// This is only done for jars with tiny-remapper remapped mixins.
	private static void validateLoomVersion(String version, String currentLoomVersion) {
		if ("0.0.0+unknown".equals(currentLoomVersion)) {
			// Unknown version, skip validation. This is the case when running from source (tests)
			return;
		}

		final String[] versionParts = version.split("\\.");
		final String[] currentVersionParts = currentLoomVersion.split("\\.");

		// Check major and minor version
		for (int i = 0; i < 2; i++) {
			final int versionPart = Integer.parseInt(versionParts[i]);
			final int currentVersionPart = Integer.parseInt(currentVersionParts[i]);

			if (versionPart > currentVersionPart) {
				throw new IllegalStateException("Mod was built with a newer version of Loom (%s), you are using Loom (%s)".formatted(version, currentLoomVersion));
			} else if (versionPart < currentVersionPart) {
				// Older version, no need to check further
				break;
			}
		}
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

	public enum MixinRemapType {
		// Jar uses refmaps, so will be remapped by mixin
		MIXIN,
		// Jar does not use refmaps, so will be remapped by tiny-remapper
		STATIC;

		public String manifestValue() {
			return name().toLowerCase(Locale.ROOT);
		}
	}
}
