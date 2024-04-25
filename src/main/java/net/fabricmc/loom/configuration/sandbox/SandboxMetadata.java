/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2024 FabricMC
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

package net.fabricmc.loom.configuration.sandbox;

import static net.fabricmc.loom.util.fmj.FabricModJsonUtils.ParseException;
import static net.fabricmc.loom.util.fmj.FabricModJsonUtils.getJsonObject;
import static net.fabricmc.loom.util.fmj.FabricModJsonUtils.readInt;
import static net.fabricmc.loom.util.fmj.FabricModJsonUtils.readString;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.fabricmc.loom.util.Platform;
import net.fabricmc.loom.util.ZipUtils;

public sealed interface SandboxMetadata permits SandboxMetadata.V1 {
	String SANDBOX_METADATA_FILENAME = "fabric-sandbox.json";

	static SandboxMetadata readFromJar(Path path) {
		try {
			JsonObject jsonObject = ZipUtils.unpackGson(path, SANDBOX_METADATA_FILENAME, JsonObject.class);
			int version = readInt(jsonObject, "version");
			return switch (version) {
			case 1 -> SandboxMetadata.V1.parseV1(jsonObject);
			default -> throw new UnsupportedOperationException("Unsupported sandbox metadata version: " + version);
			};
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to read: " + SANDBOX_METADATA_FILENAME, e);
		}
	}

	/**
	 * @return The main class of the sandbox.
	 */
	String mainClass();

	/**
	 * @param platform The platform to check.
	 * @return True if the sandbox supports the platform, false otherwise.
	 */
	boolean supportsPlatform(Platform platform);

	record V1(String mainClass, Map<OperatingSystem, List<Architecture>> supportedPlatforms) implements SandboxMetadata {
		static V1 parseV1(JsonObject jsonObject) {
			String mainClass = readString(jsonObject, "mainClass");
			JsonObject platforms = getJsonObject(jsonObject, "platforms");

			Map<OperatingSystem, List<Architecture>> supportedPlatforms = new HashMap<>();

			for (Map.Entry<String, JsonElement> entry : platforms.entrySet()) {
				if (!entry.getValue().isJsonArray()) {
					throw new ParseException("Unexpected json array type for key (%s)", entry.getKey());
				}

				List<Architecture> architectures = new ArrayList<>();

				for (JsonElement element : entry.getValue().getAsJsonArray()) {
					if (!(element.isJsonPrimitive() && element.getAsJsonPrimitive().isString())) {
						throw new ParseException("Unexpected json primitive type for key (%s)", entry.getKey());
					}

					architectures.add(parseArchitecture(element.getAsString()));
				}

				supportedPlatforms.put(parseOperatingSystem(entry.getKey()), Collections.unmodifiableList(architectures));
			}

			return new V1(mainClass, Collections.unmodifiableMap(supportedPlatforms));
		}

		@Override
		public boolean supportsPlatform(Platform platform) {
			for (Map.Entry<OperatingSystem, List<Architecture>> entry : supportedPlatforms.entrySet()) {
				if (!entry.getKey().compatibleWith(platform)) {
					continue;
				}

				for (Architecture architecture : entry.getValue()) {
					if (architecture.compatibleWith(platform)) {
						return true;
					}
				}
			}

			return false;
		}
	}

	enum OperatingSystem {
		WINDOWS,
		MAC_OS,
		LINUX;

		public boolean compatibleWith(Platform platform) {
			final Platform.OperatingSystem operatingSystem = platform.getOperatingSystem();

			return switch (this) {
			case WINDOWS -> operatingSystem.isWindows();
			case MAC_OS -> operatingSystem.isMacOS();
			case LINUX -> operatingSystem.isLinux();
			};
		}
	}

	enum Architecture {
		X86_64,
		ARM64;

		public boolean compatibleWith(Platform platform) {
			final Platform.Architecture architecture = platform.getArchitecture();

			if (!architecture.is64Bit()) {
				return false;
			}

			return switch (this) {
			case X86_64 -> !architecture.isArm();
			case ARM64 -> architecture.isArm();
			};
		}
	}

	private static OperatingSystem parseOperatingSystem(String os) {
		return switch (os) {
		case "windows" -> OperatingSystem.WINDOWS;
		case "macos" -> OperatingSystem.MAC_OS;
		case "linux" -> OperatingSystem.LINUX;
		default -> throw new ParseException("Unsupported sandbox operating system: %s", os);
		};
	}

	private static Architecture parseArchitecture(String arch) {
		return switch (arch) {
		case "x86_64" -> Architecture.X86_64;
		case "arm64" -> Architecture.ARM64;
		default -> throw new ParseException("Unsupported sandbox architecture: %s", arch);
		};
	}
}
