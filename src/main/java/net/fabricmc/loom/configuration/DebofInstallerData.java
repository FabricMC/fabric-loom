/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 FabricMC
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

package net.fabricmc.loom.configuration;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.Optional;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.configuration.processors.speccontext.DebofConfiguration;
import net.fabricmc.loom.util.ZipUtils;
import net.fabricmc.loom.util.fmj.FabricModJson;
import net.fabricmc.loom.util.fmj.FabricModJsonFactory;

public class DebofInstallerData {
	private static final Logger LOGGER = LoggerFactory.getLogger(DebofInstallerData.class);

	public static void findAndApply(Project project) {
		for (DebofConfiguration debofConfiguration : DebofConfiguration.ALL) {
			for (Configuration configuration : debofConfiguration.getConfigurations(project)) {
				Optional<InstallerData> installerData = configuration.getFiles().parallelStream()
						.map(DebofInstallerData::getInstaller)
						.filter(Objects::nonNull)
						.findFirst();

				if (installerData.isPresent()) {
					LOGGER.info("Applying installer data from configuration '{}'", configuration.getName());
					installerData.get().applyToProject(project);
					return;
				}
			}
		}

		LOGGER.info("No installer data found in any configuration.");
	}

	@Nullable
	private static InstallerData getInstaller(File file) {
		try {
			byte[] installerData = ZipUtils.unpackNullable(file.toPath(), InstallerData.INSTALLER_PATH);

			if (installerData == null) {
				return null;
			}

			FabricModJson fabricModJson = FabricModJsonFactory.createFromZip(file.toPath());
			LOGGER.info("Found installer in mod {} version {}", fabricModJson.getId(), fabricModJson.getModVersion());
			return InstallerData.fromBytes(installerData, fabricModJson.getModVersion());
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to read " + file, e);
		}
	}
}
