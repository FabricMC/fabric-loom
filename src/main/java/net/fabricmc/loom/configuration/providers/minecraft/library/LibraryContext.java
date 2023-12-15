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

import java.util.Arrays;

import org.gradle.api.JavaVersion;

import net.fabricmc.loom.configuration.providers.minecraft.MinecraftVersionMeta;
import net.fabricmc.loom.util.Platform;

public final class LibraryContext {
	private final MinecraftVersionMeta versionMeta;
	private final JavaVersion javaVersion;

	public LibraryContext(MinecraftVersionMeta versionMeta, JavaVersion javaVersion) {
		this.versionMeta = versionMeta;
		this.javaVersion = javaVersion;
	}

	/**
	 * @return True when the Minecraft libraries support ARM64 MacOS
	 */
	public boolean supportsArm64(Platform.OperatingSystem operatingSystem) {
		final String osName = switch (operatingSystem) {
		case MAC_OS -> "macos";
		case WINDOWS -> "windows";
		case LINUX -> "linux";
		};

		return versionMeta.libraries().stream()
				.anyMatch(library -> library.name().startsWith("org.lwjgl:lwjgl:3") && library.name().endsWith(":natives-%s-arm64".formatted(osName)));
	}

	/**
	 * @return True when the Minecraft libraries support Java 19 or later
	 */
	public boolean supportsJava19OrLater() {
		return versionMeta.libraries().stream().filter(library -> library.name().startsWith("org.lwjgl:lwjgl:")).anyMatch(library -> {
			final String[] split = library.name().split(":");

			if (split.length != 3) {
				return false;
			}

			final String version = split[2];

			final int[] versionSplit = Arrays.stream(version.split("\\."))
					.mapToInt(Integer::parseInt)
					.toArray();

			// LWJGL 4 or newer
			if (versionSplit[0] > 3) {
				return true;
			}

			// LWJGL 3.4 or newer
			if (versionSplit[0] == 3 && versionSplit[1] > 3) {
				return true;
			}

			// LWJGL 3.3.2 or newer
			if (versionSplit[0] == 3 && versionSplit[1] == 3 && versionSplit[2] >= 2) {
				return true;
			}

			return false;
		});
	}

	/**
	 * @return True when using LWJGL 3
	 */
	public boolean usesLWJGL3() {
		return versionMeta.libraries().stream()
				.anyMatch(library -> library.name().startsWith("org.lwjgl:lwjgl:3"));
	}

	/**
	 * @return True when the Minecraft natives are on the classpath, as opposed to being extracted
	 */
	public boolean hasClasspathNatives() {
		return !versionMeta.hasNativesToExtract();
	}

	/**
	 * @return True when there is an exact match for this library
	 */
	public boolean hasLibrary(String name) {
		return versionMeta.libraries().stream()
				.anyMatch(library -> library.name().equals(name));
	}

	/**
	 * @return True when the current Java version is 19 or later
	 */
	public boolean isJava19OrLater() {
		return javaVersion.isCompatibleWith(JavaVersion.VERSION_19);
	}
}
