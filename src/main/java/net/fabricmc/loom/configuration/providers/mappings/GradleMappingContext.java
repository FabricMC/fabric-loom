/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2018-2021 FabricMC
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

package net.fabricmc.loom.configuration.providers.mappings;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.logging.Logger;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.providers.MinecraftProvider;

public class GradleMappingContext implements MappingContext {
	private final Project project;
	private final LoomGradleExtension extension;
	private File workingDir;

	public GradleMappingContext(Project project) {
		this.project = project;
		this.extension = LoomGradleExtension.get(project);
	}

	@Override
	public File mavenFile(String mavenNotation) {
		Configuration configuration = project.getConfigurations().detachedConfiguration(project.getDependencies().create(mavenNotation));
		return configuration.getSingleFile();
	}

	@Override
	public MappingsProvider mappingsProvider() {
		return extension.getMappingsProvider();
	}

	@Override
	public MinecraftProvider minecraftProvider() {
		return extension.getMinecraftProvider();
	}

	@Override
	public File workingDirectory() {
		if (workingDir == null) {
			workingDir = new File(mappingsProvider().getMappingsDir().toFile(), "layered/" + minecraftProvider().minecraftVersion());

			if (workingDir.exists()) {
				try {
					FileUtils.deleteDirectory(workingDir);
				} catch (IOException e) {
					getLogger().warn("Failed to cleanup layered mappings working directory: {}", e.getMessage());
				}
			}

			workingDir.mkdirs();
		}

		return workingDir;
	}

	@Override
	public File workingDirectory(String name) {
		File file = new File(workingDirectory(), name);
		file.mkdirs();

		return file;
	}

	@Override
	public Logger getLogger() {
		return project.getLogger();
	}
}
