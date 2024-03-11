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

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.util.Platform;

@SuppressWarnings("unused")
public record MinecraftVersionMeta(
		Object arguments,
		AssetIndex assetIndex,
		String assets,
		int complianceLevel,
		Map<String, Download> downloads,
		String id,
		List<Library> libraries,
		Object logging,
		String mainClass,
		int minimumLauncherVersion,
		String releaseTime,
		String time,
		String type,
		@Nullable JavaVersion javaVersion
) {
	private static Map<Platform.OperatingSystem, String> OS_NAMES = Map.of(
			Platform.OperatingSystem.WINDOWS, "windows",
			Platform.OperatingSystem.MAC_OS, "osx",
			Platform.OperatingSystem.LINUX, "linux"
	);

	public Download download(String key) {
		return downloads().get(key);
	}

	public boolean isVersionOrNewer(String releaseTime) {
		return this.releaseTime().compareTo(releaseTime) >= 0;
	}

	public boolean hasNativesToExtract() {
		return libraries.stream().anyMatch(Library::hasNatives);
	}

	public record AssetIndex(String id, long totalSize, String path, String sha1, long size, String url) {
		public String fabricId(String version) {
			return id.equals(version) ? version : version + "-" + id;
		}
	}

	public record Library(Downloads downloads, String name, Map<String, String> natives, List<Rule> rules, Object extract) {
		public boolean isValidForOS(Platform platform) {
			if (rules == null) {
				// No rules allow everything.
				return true;
			}

			boolean valid = false;

			for (Rule rule : this.rules) {
				if (rule.appliesToOS(platform)) {
					valid = rule.isAllowed();
				}
			}

			return valid;
		}

		public boolean hasNatives() {
			return this.natives != null;
		}

		public boolean hasNativesForOS(Platform platform) {
			if (!hasNatives()) {
				return false;
			}

			if (classifierForOS(platform) == null) {
				return false;
			}

			return isValidForOS(platform);
		}

		@Nullable
		public Download classifierForOS(Platform platform) {
			String classifier = natives.get(OS_NAMES.get(platform.getOperatingSystem()));

			if (classifier == null) {
				return null;
			}

			if (platform.getArchitecture().isArm() && platform.getArchitecture().is64Bit()) {
				// Default to the arm64 natives, if not found fallback.
				final Download armNative = downloads().classifier(classifier + "-arm64");

				if (armNative != null) {
					return armNative;
				}
			}

			// Used in the twitch library in 1.7.10
			classifier = classifier.replace("${arch}", platform.getArchitecture().is64Bit() ? "64" : "32");

			return downloads().classifier(classifier);
		}

		public Download artifact() {
			if (downloads() == null) {
				return null;
			}

			return downloads().artifact();
		}
	}

	public record Downloads(Download artifact, Map<String, Download> classifiers) {
		@Nullable
		public Download classifier(String os) {
			return classifiers.get(os);
		}
	}

	public record Rule(String action, OS os) {
		public boolean appliesToOS(Platform platform) {
			return os() == null || os().isValidForOS(platform);
		}

		public boolean isAllowed() {
			return action().equals("allow");
		}
	}

	public record OS(String name) {
		public boolean isValidForOS(Platform platform) {
			return name() == null || name().equalsIgnoreCase(OS_NAMES.get(platform.getOperatingSystem()));
		}
	}

	public record Download(String path, String sha1, long size, String url) {
		public File relativeFile(File baseDirectory) {
			Objects.requireNonNull(path(), "Cannot get relative file from a null path");
			return new File(baseDirectory, path());
		}
	}

	public record JavaVersion(String component, int majorVersion) {
	}
}
