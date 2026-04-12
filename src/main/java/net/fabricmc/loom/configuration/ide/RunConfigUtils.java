/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2026 FabricMC
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

package net.fabricmc.loom.configuration.ide;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.RunConfiguration;
import net.fabricmc.loom.configuration.InstallerData;
import net.fabricmc.loom.util.Constants;

public class RunConfigUtils {
	private static final Logger LOGGER = LoggerFactory.getLogger(RunConfigUtils.class);

	public static void createRunDirectory(RunConfiguration runConfiguration) throws IOException {
		Path runDirectory = runConfiguration.getRunDirectory().getAsFile().get().toPath();

		if (!Files.exists(runDirectory)) {
			Files.createDirectories(runDirectory);
		} else if (!Files.isDirectory(runDirectory)) {
			LOGGER.warn("Run directory {} is not a directory", runDirectory);
		}
	}

	@Nullable
	public static String getMainClass(String side, LoomGradleExtension extension) {
		InstallerData installerData = extension.getInstallerData();

		if (installerData == null) {
			return getDefaultMainClass(side);
		}

		JsonObject installerJson = installerData.installerJson();

		if (installerJson != null && installerJson.has("mainClass")) {
			JsonElement mainClassJson = installerJson.get("mainClass");

			String mainClassName = "";

			if (mainClassJson.isJsonObject()) {
				JsonObject mainClassesJson = mainClassJson.getAsJsonObject();

				if (mainClassesJson.has(side)) {
					mainClassName = mainClassesJson.get(side).getAsString();
				}
			} else {
				mainClassName = mainClassJson.getAsString();
			}

			return mainClassName;
		}

		return getDefaultMainClass(side);
	}

	@Nullable
	private static String getDefaultMainClass(String side) {
		return switch (side) {
		case "client" -> Constants.Knot.KNOT_CLIENT;
		case "server" -> Constants.Knot.KNOT_SERVER;
		default -> null;
		};
	}
}
