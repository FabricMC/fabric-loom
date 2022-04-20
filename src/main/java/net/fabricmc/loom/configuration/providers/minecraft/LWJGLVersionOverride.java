/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021 FabricMC
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

import static net.fabricmc.loom.util.OperatingSystem.LINUX;
import static net.fabricmc.loom.util.OperatingSystem.MAC_OS;
import static net.fabricmc.loom.util.OperatingSystem.WINDOWS;

import java.util.List;

import org.gradle.api.Project;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.util.Architecture;
import net.fabricmc.loom.util.OperatingSystem;

public class LWJGLVersionOverride {
	public static final String LWJGL_VERSION = "3.3.1";
	@Nullable
	public static final String NATIVE_CLASSIFIER = getNativesClassifier();

	public static final List<String> DEPENDENCIES = List.of(
			"org.lwjgl:lwjgl:" + LWJGL_VERSION,
			"org.lwjgl:lwjgl-glfw:" + LWJGL_VERSION,
			"org.lwjgl:lwjgl-jemalloc:" + LWJGL_VERSION,
			"org.lwjgl:lwjgl-openal:" + LWJGL_VERSION,
			"org.lwjgl:lwjgl-opengl:" + LWJGL_VERSION,
			"org.lwjgl:lwjgl-stb:" + LWJGL_VERSION,
			"org.lwjgl:lwjgl-tinyfd:" + LWJGL_VERSION
	);
	public static final List<String> NATIVES = DEPENDENCIES.stream().map(s -> s + ":" + NATIVE_CLASSIFIER).toList();

	public static final List<String> MACOS_DEPENDENCIES = List.of(
			"ca.weblite:java-objc-bridge:1.1"
	);
	// Same for now, as java-objc-bridge includes the natives in the main jar.
	public static final List<String> MACOS_NATIVES = MACOS_DEPENDENCIES;

	/**
	 * Update lwjgl by default when running on arm and a supported configuration.
	 */
	public static boolean overrideByDefault(MinecraftVersionMeta versionMeta) {
		if (NATIVE_CLASSIFIER == null || !Architecture.CURRENT.isArm()) {
			return false;
		}

		boolean supportedLwjglVersion = versionMeta.libraries().stream()
				.anyMatch(library -> library.name().startsWith("org.lwjgl:lwjgl:3"));

		boolean hasExistingNatives = versionMeta.libraries().stream()
				.filter(library -> library.name().startsWith("org.lwjgl:lwjgl"))
				.anyMatch(MinecraftVersionMeta.Library::hasNativesForOS);

		// Is LWJGL 3, and doesn't have any existing compatible LWGL natives.
		return supportedLwjglVersion && !hasExistingNatives;
	}

	public static boolean forceOverride(Project project) {
		return project.getProperties().get("fabric.loom.override-lwjgl") != null;
	}

	@Nullable
	private static String getNativesClassifier() {
		return switch (OperatingSystem.CURRENT_OS) {
		case WINDOWS -> getWindowsClassifier();
		case MAC_OS -> getMacOSClassifier();
		case LINUX -> getLinuxClassifier();
		default -> null;
		};
	}

	private static String getWindowsClassifier() {
		if (Architecture.CURRENT.is64Bit()) {
			if (Architecture.CURRENT.isArm()) {
				// Arm 64 bit
				return "natives-windows-arm64";
			}

			// None arm 64bit
			return "natives-windows";
		}

		// All 32bit, including arm
		return "natives-windows-x86";
	}

	private static String getMacOSClassifier() {
		if (Architecture.CURRENT.isArm()) {
			// Apple silicone arm
			return "natives-macos-arm64";
		}

		// Intel 64bit.
		return "natives-macos";
	}

	private static String getLinuxClassifier() {
		if (Architecture.CURRENT.isArm()) {
			return Architecture.CURRENT.is64Bit() ? "natives-linux-arm64" : "natives-linux-arm32";
		}

		return "natives-linux";
	}
}
