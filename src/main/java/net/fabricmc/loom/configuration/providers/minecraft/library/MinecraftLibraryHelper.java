/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2023 FabricMC
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

package net.fabricmc.loom.configuration.providers.minecraft.library;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.fabricmc.loom.configuration.providers.BundleMetadata;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftVersionMeta;
import net.fabricmc.loom.util.Platform;

/**
 * Utils to get the Minecraft libraries for a given platform, no processing is applied.
 */
public class MinecraftLibraryHelper {
	private static final Pattern NATIVES_PATTERN = Pattern.compile("^(?<group>.*)/(.*?)/(?<version>.*)/((?<name>.*?)-(\\k<version>)-)(?<classifier>.*).jar$");

	public static List<Library> getLibrariesForPlatform(MinecraftVersionMeta versionMeta, Platform platform) {
		var libraries = new ArrayList<Library>();

		for (MinecraftVersionMeta.Library library : versionMeta.libraries()) {
			if (!library.isValidForOS(platform)) {
				continue;
			}

			if (library.artifact() != null) {
				Library mavenLib = Library.fromMaven(library.name(), Library.Target.COMPILE);

				// Versions that have the natives on the classpath, attempt to target them as natives.
				if (mavenLib.classifier() != null && mavenLib.classifier().startsWith("natives-")) {
					mavenLib = mavenLib.withTarget(Library.Target.NATIVES);
				}

				libraries.add(mavenLib);
			}

			if (library.hasNativesForOS(platform)) {
				final MinecraftVersionMeta.Download download = library.classifierForOS(platform);

				if (download != null) {
					libraries.add(downloadToLibrary(download));
				}
			}
		}

		return Collections.unmodifiableList(libraries);
	}

	private static Library downloadToLibrary(MinecraftVersionMeta.Download download) {
		final String path = download.path();
		final Matcher matcher = NATIVES_PATTERN.matcher(path);

		if (!matcher.find()) {
			throw new IllegalStateException("Failed to match regex for natives path : " + path);
		}

		final String group = matcher.group("group").replace("/", ".");
		final String name = matcher.group("name");
		final String version = matcher.group("version");
		final String classifier = matcher.group("classifier");
		final String dependencyNotation = "%s:%s:%s:%s".formatted(group, name, version, classifier);

		return Library.fromMaven(dependencyNotation, Library.Target.NATIVES);
	}

	public static List<Library> getServerLibraries(BundleMetadata bundleMetadata) {
		Objects.requireNonNull(bundleMetadata);

		var libraries = new ArrayList<Library>();

		for (BundleMetadata.Entry library : bundleMetadata.libraries()) {
			libraries.add(Library.fromMaven(library.name(), Library.Target.COMPILE));
		}

		return libraries;
	}
}
