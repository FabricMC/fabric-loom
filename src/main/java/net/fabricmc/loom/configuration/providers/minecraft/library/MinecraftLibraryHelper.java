package net.fabricmc.loom.configuration.providers.minecraft.library;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

			Library mavenLib = Library.fromMaven(library.name(), Library.Target.COMPILE);

			// Versions that have the natives on the classpath, attempt to target them as natives.
			if (mavenLib.classifier() != null && mavenLib.classifier().startsWith("natives-")) {
				mavenLib = mavenLib.withTarget(Library.Target.NATIVES);
			}

			libraries.add(mavenLib);

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
}
