/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2018-2022 FabricMC
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

package net.fabricmc.loom.configuration.providers.minecraft;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.gradle.api.Project;
import org.gradle.api.artifacts.ExternalModuleDependency;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.LoomRepositoryPlugin;
import net.fabricmc.loom.configuration.providers.BundleMetadata;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.OperatingSystem;

public class MinecraftLibraryProvider {
	private static final Pattern NATIVES_PATTERN = Pattern.compile("^(?<group>.*)/(.*?)/(?<version>.*)/((?<name>.*?)-(\\k<version>)-)(?<classifier>.*).jar$");

	public void provide(MinecraftProvider minecraftProvider, Project project) {
		final LoomGradleExtension extension = LoomGradleExtension.get(project);
		final MinecraftJarConfiguration jarConfiguration = extension.getMinecraftJarConfiguration().get();
		final MinecraftVersionMeta versionInfo = minecraftProvider.getVersionInfo();
		final BundleMetadata serverBundleMetadata = minecraftProvider.getServerBundleMetadata();
		final boolean runtimeOnlyLog4j = extension.getRuntimeOnlyLog4j().get();

		final boolean overrideLWJGL = LWJGLVersionOverride.overrideByDefault(versionInfo) || LWJGLVersionOverride.forceOverride(project) || Boolean.getBoolean("loom.test.lwjgloverride");
		final boolean isMacOS = OperatingSystem.CURRENT_OS.equals(OperatingSystem.MAC_OS);

		if (overrideLWJGL) {
			project.getLogger().warn("Loom is upgrading Minecraft's LWJGL version to {}", LWJGLVersionOverride.LWJGL_VERSION);
		}

		if (versionInfo.hasNativesToExtract()) {
			extension.createLazyConfiguration(Constants.Configurations.MINECRAFT_NATIVES, configuration -> configuration.setTransitive(false));
		}

		for (MinecraftVersionMeta.Library library : versionInfo.libraries()) {
			if (overrideLWJGL && library.name().startsWith("org.lwjgl")) {
				continue;
			}

			if (library.isValidForOS() && !library.hasNatives() && library.artifact() != null) {
				// 1.4.7 contains an LWJGL version with an invalid maven pom, set the metadata sources to not use the pom for this version.
				if ("org.lwjgl.lwjgl:lwjgl:2.9.1-nightly-20130708-debug3".equals(library.name()) || "org.lwjgl.lwjgl:lwjgl:2.9.1-nightly-20131017".equals(library.name())) {
					LoomRepositoryPlugin.setupForLegacyVersions(project);
				} else if (library.name().startsWith("org.ow2.asm:asm-all")) {
					// Don't want asm-all, use the modern split version.
					continue;
				}

				if (runtimeOnlyLog4j && library.name().startsWith("org.apache.logging.log4j")) {
					// Make log4j a runtime only dep to force slf4j.
					project.getDependencies().add(Constants.Configurations.MINECRAFT_RUNTIME_DEPENDENCIES, library.name());
				} else if (serverBundleMetadata != null && isLibraryInBundle(serverBundleMetadata, library)) {
					project.getDependencies().add(Constants.Configurations.MINECRAFT_SERVER_DEPENDENCIES, library.name());
				} else if (jarConfiguration.getSupportedEnvironments().contains("client")) {
					// Client only library, or legacy version
					project.getDependencies().add(Constants.Configurations.MINECRAFT_DEPENDENCIES, library.name());
				} else {
					project.getLogger().debug("Minecraft library ({}) was not added to any configuration", library.name());
				}
			}

			if (library.hasNativesForOS()) {
				MinecraftVersionMeta.Download nativeDownload = library.classifierForOS();

				if (nativeDownload == null) {
					continue;
				}

				final String path = nativeDownload.path();
				final Matcher matcher = NATIVES_PATTERN.matcher(path);

				if (!matcher.find()) {
					project.getLogger().warn("Failed to match regex for natives path : " + path);
					continue;
				}

				final String group = matcher.group("group").replace("/", ".");
				final String name = matcher.group("name");
				final String version = matcher.group("version");
				final String classifier = matcher.group("classifier");

				final String dependencyNotation = "%s:%s:%s:%s".formatted(group, name, version, classifier);

				if (overrideLWJGL && isMacOS && "java-objc-bridge".equals(name)) {
					// Mojang split out the natives into their own jar, skip over Mojang's jar and use the official jar later on.
					continue;
				}

				project.getLogger().debug("Add native dependency '{}'", dependencyNotation);
				project.getDependencies().add(Constants.Configurations.MINECRAFT_NATIVES, dependencyNotation);
			}
		}

		if (overrideLWJGL) {
			LWJGLVersionOverride.DEPENDENCIES.forEach(s -> project.getDependencies().add(Constants.Configurations.MINECRAFT_DEPENDENCIES, s));
			LWJGLVersionOverride.NATIVES.forEach(s -> project.getDependencies().add(Constants.Configurations.MINECRAFT_NATIVES, s));

			if (isMacOS) {
				LWJGLVersionOverride.MACOS_DEPENDENCIES.forEach(s -> project.getDependencies().add(Constants.Configurations.MINECRAFT_DEPENDENCIES, s));
				LWJGLVersionOverride.MACOS_NATIVES.forEach(s -> project.getDependencies().add(Constants.Configurations.MINECRAFT_NATIVES, s));
			}

			// Add the native support mod that fixes a handful of issues related to the LWJGL update at runtime.
			ExternalModuleDependency dependency = (ExternalModuleDependency) project.getDependencies().create(Constants.Dependencies.NATIVE_SUPPORT + Constants.Dependencies.Versions.NATIVE_SUPPORT_VERSION);
			dependency.setTransitive(false);
			project.getDependencies().add("modLocalRuntime", dependency);
		}
	}

	private static boolean isLibraryInBundle(BundleMetadata bundleMetadata, MinecraftVersionMeta.Library library) {
		return bundleMetadata.libraries().stream().anyMatch(entry -> entry.name().equals(library.name()));
	}
}
