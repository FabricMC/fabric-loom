/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021-2022 FabricMC
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

import java.util.List;

import org.gradle.api.Project;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.Platform;

public class LWJGLVersionOverride {
	public static final String LWJGL_VERSION = "3.3.1";

	public static final List<String> DEPENDENCIES = List.of(
			"org.lwjgl:lwjgl:" + LWJGL_VERSION,
			"org.lwjgl:lwjgl-glfw:" + LWJGL_VERSION,
			"org.lwjgl:lwjgl-jemalloc:" + LWJGL_VERSION,
			"org.lwjgl:lwjgl-openal:" + LWJGL_VERSION,
			"org.lwjgl:lwjgl-opengl:" + LWJGL_VERSION,
			"org.lwjgl:lwjgl-stb:" + LWJGL_VERSION,
			"org.lwjgl:lwjgl-tinyfd:" + LWJGL_VERSION
	);

	public static final List<String> MACOS_DEPENDENCIES = List.of(
			"ca.weblite:java-objc-bridge:1.1"
	);
	// Same for now, as java-objc-bridge includes the natives in the main jar.
	public static final List<String> MACOS_NATIVES = MACOS_DEPENDENCIES;

	private final Platform platform;

	public LWJGLVersionOverride(Platform platform) {
		this.platform = platform;
	}

	/**
	 * Update lwjgl by default when running on arm and a supported configuration.
	 */
	public boolean overrideByDefault(MinecraftVersionMeta versionMeta) {
		if (getNativesClassifier() == null || !platform.getArchitecture().isArm()) {
			return false;
		}

		boolean supportedLwjglVersion = versionMeta.libraries().stream()
				.anyMatch(library -> library.name().startsWith("org.lwjgl:lwjgl:3"));

		boolean hasExistingNatives = versionMeta.libraries().stream()
				.filter(library -> library.name().startsWith("org.lwjgl:lwjgl"))
				.anyMatch(library -> library.hasNativesForOS(platform));

		// Is LWJGL 3, and doesn't have any existing compatible LWGL natives.
		return supportedLwjglVersion && !hasExistingNatives;
	}

	public boolean forceOverride(Project project) {
		return project.getProperties().get("fabric.loom.override-lwjgl") != null;
	}

	public void applyOverrides(Project project, boolean isMacOS) {
		final String nativeClassifier = getNativesClassifier();

		for (String dependency : DEPENDENCIES) {
			project.getDependencies().add(Constants.Configurations.MINECRAFT_DEPENDENCIES, dependency);
			project.getDependencies().add(Constants.Configurations.MINECRAFT_NATIVES, dependency + ":" + nativeClassifier);
		}

		if (isMacOS) {
			MACOS_DEPENDENCIES.forEach(s -> project.getDependencies().add(Constants.Configurations.MINECRAFT_DEPENDENCIES, s));
			MACOS_NATIVES.forEach(s -> project.getDependencies().add(Constants.Configurations.MINECRAFT_NATIVES, s));
		}

		// Add the native support mod that fixes a handful of issues related to the LWJGL update at runtime.
		ExternalModuleDependency dependency = (ExternalModuleDependency) project.getDependencies().create(Constants.Dependencies.NATIVE_SUPPORT + Constants.Dependencies.Versions.NATIVE_SUPPORT_VERSION);
		dependency.setTransitive(false);
		project.getDependencies().add("modLocalRuntime", dependency);
	}

	@Nullable
	private String getNativesClassifier() {
		return switch (platform.getOperatingSystem()) {
		case WINDOWS -> getWindowsClassifier();
		case MAC_OS -> getMacOSClassifier();
		case LINUX -> getLinuxClassifier();
		default -> null;
		};
	}

	private String getWindowsClassifier() {
		if (platform.getArchitecture().is64Bit()) {
			if (platform.getArchitecture().isArm()) {
				// Arm 64 bit
				return "natives-windows-arm64";
			}

			// None arm 64bit
			return "natives-windows";
		}

		// All 32bit, including arm
		return "natives-windows-x86";
	}

	private String getMacOSClassifier() {
		if (platform.getArchitecture().isArm()) {
			// Apple silicone arm
			return "natives-macos-arm64";
		}

		// Intel 64bit.
		return "natives-macos";
	}

	private String getLinuxClassifier() {
		if (platform.getArchitecture().isArm()) {
			return platform.getArchitecture().is64Bit() ? "natives-linux-arm64" : "natives-linux-arm32";
		}

		return "natives-linux";
	}
}
