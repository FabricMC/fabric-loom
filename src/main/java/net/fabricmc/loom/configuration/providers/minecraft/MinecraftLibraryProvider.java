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
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleDependency;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.LoomRepositoryPlugin;
import net.fabricmc.loom.configuration.providers.BundleMetadata;
import net.fabricmc.loom.util.Architecture;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.OperatingSystem;

public class MinecraftLibraryProvider {
	private static final Pattern NATIVES_PATTERN = Pattern.compile("^(?<group>.*)/(.*?)/(?<version>.*)/((?<name>.*?)-(\\k<version>)-)(?<classifier>.*).jar$");
	private static final boolean IS_MACOS = OperatingSystem.CURRENT_OS.equals(OperatingSystem.MAC_OS);

	private final Project project;
	private final MinecraftVersionMeta versionInfo;
	private final BundleMetadata serverBundleMetadata;
	private final boolean runtimeOnlyLog4j;
	private final boolean provideClient;
	private final boolean provideServer;

	public MinecraftLibraryProvider(MinecraftProvider minecraftProvider, Project project) {
		final LoomGradleExtension extension = LoomGradleExtension.get(project);
		final MinecraftJarConfiguration jarConfiguration = extension.getMinecraftJarConfiguration().get();

		this.project = project;
		this.versionInfo = minecraftProvider.getVersionInfo();
		this.serverBundleMetadata = minecraftProvider.getServerBundleMetadata();
		this.runtimeOnlyLog4j = extension.getRuntimeOnlyLog4j().get();
		this.provideClient = jarConfiguration.getSupportedEnvironments().contains("client");
		this.provideServer = jarConfiguration.getSupportedEnvironments().contains("server");
		assert provideClient || provideServer;
	}

	private void addDependency(String configuration, Object dependency) {
		Dependency created = project.getDependencies().add(configuration, dependency);

		// The launcher doesn't download transitive deps, so neither will we.
		// This will also prevent a LaunchWrapper library dependency from pulling in outdated ASM jars.
		if (created instanceof ModuleDependency md) {
			md.setTransitive(false);
		}
	}

	public void provide() {
		if (provideClient) {
			// Modern 1.19 version put the natives on the classpath.
			final boolean hasNativesToExtract = versionInfo.hasNativesToExtract();
			final boolean overrideLWJGL = hasNativesToExtract && (LWJGLVersionOverride.overrideByDefault(versionInfo) || LWJGLVersionOverride.forceOverride(project) || Boolean.getBoolean("loom.test.lwjgloverride"));

			if (overrideLWJGL) {
				project.getLogger().warn("Loom is upgrading Minecraft's LWJGL version to {}", LWJGLVersionOverride.LWJGL_VERSION);
			}

			if (hasNativesToExtract) {
				// Create a configuration for
				project.getConfigurations().register(Constants.Configurations.MINECRAFT_NATIVES, configuration -> configuration.setTransitive(false));
			}

			provideClientLibraries(overrideLWJGL, hasNativesToExtract);

			if (overrideLWJGL) {
				LWJGLVersionOverride.applyOverrides(project, IS_MACOS);
			}
		}

		if (provideServer) {
			provideServerLibraries();
		}
	}

	private void provideClientLibraries(boolean overrideLWJGL, boolean hasNativesToExtract) {
		final boolean isArm = Architecture.CURRENT.isArm();
		final boolean classpathArmNatives = !hasNativesToExtract && isArm && !IS_MACOS;

		if (classpathArmNatives) {
			LoomRepositoryPlugin.forceLWJGLFromMavenCentral(project);
		}

		for (MinecraftVersionMeta.Library library : versionInfo.libraries()) {
			if (overrideLWJGL && library.name().startsWith("org.lwjgl")) {
				// Skip over Minecraft's LWJGL version, will will replace this with a newer version later.
				continue;
			}

			if (library.isValidForOS() && !library.hasNatives() && library.artifact() != null) {
				final String name = library.name();

				if ("org.lwjgl.lwjgl:lwjgl:2.9.1-nightly-20130708-debug3".equals(name) || "org.lwjgl.lwjgl:lwjgl:2.9.1-nightly-20131017".equals(name)) {
					// 1.4.7 contains an LWJGL version with an invalid maven pom, set the metadata sources to not use the pom for this version.
					LoomRepositoryPlugin.setupForLegacyVersions(project);
				}

				if (name.startsWith("org.ow2.asm:asm-all")) {
					// Don't want asm-all, use the modern split version.
					continue;
				}

				if (runtimeOnlyLog4j && name.startsWith("org.apache.logging.log4j")) {
					// Make log4j a runtime only dep to force slf4j.
					addDependency(Constants.Configurations.MINECRAFT_RUNTIME_DEPENDENCIES, name);
					continue;
				}

				if (classpathArmNatives && name.startsWith("org.lwjgl:")
						&& (name.endsWith("natives-windows") || name.endsWith("natives-linux"))) {
					// Add windows and Linux arm64 natives for modern classpath native MC versions.
					addDependency(Constants.Configurations.MINECRAFT_DEPENDENCIES, name + "-arm64");
				}

				addDependency(Constants.Configurations.MINECRAFT_DEPENDENCIES, name);
			}

			if (library.hasNativesForOS()) {
				provideNativesForLibrary(library, overrideLWJGL, IS_MACOS);
			}
		}
	}

	private void provideServerLibraries() {
		if (serverBundleMetadata != null) {
			for (BundleMetadata.Entry library : serverBundleMetadata.libraries()) {
				if (runtimeOnlyLog4j && library.name().startsWith("org.apache.logging.log4j")) {
					// Make log4j a runtime only dep to force slf4j.
					addDependency(Constants.Configurations.MINECRAFT_RUNTIME_DEPENDENCIES, library.name());
					continue;
				}

				addDependency(Constants.Configurations.MINECRAFT_SERVER_DEPENDENCIES, library.name());
			}
		}
	}

	private void provideNativesForLibrary(MinecraftVersionMeta.Library library, boolean overrideLWJGL, boolean isMacOS) {
		MinecraftVersionMeta.Download nativeDownload = library.classifierForOS();

		if (nativeDownload == null) {
			return;
		}

		final String path = nativeDownload.path();
		final Matcher matcher = NATIVES_PATTERN.matcher(path);

		if (!matcher.find()) {
			project.getLogger().warn("Failed to match regex for natives path : " + path);
			return;
		}

		final String group = matcher.group("group").replace("/", ".");
		final String name = matcher.group("name");
		final String version = matcher.group("version");
		final String classifier = matcher.group("classifier");

		final String dependencyNotation = "%s:%s:%s:%s".formatted(group, name, version, classifier);

		if (overrideLWJGL && isMacOS && "java-objc-bridge".equals(name)) {
			// Mojang split out the natives into their own jar, skip over Mojang's jar and use the official jar later on.
			return;
		}

		project.getLogger().debug("Add native dependency '{}'", dependencyNotation);
		addDependency(Constants.Configurations.MINECRAFT_NATIVES, dependencyNotation);
	}
}
