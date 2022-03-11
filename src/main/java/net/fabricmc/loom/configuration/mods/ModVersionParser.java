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

package net.fabricmc.loom.configuration.mods;

import java.io.File;

import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.api.LoomGradleExtensionAPI;
import net.fabricmc.loom.configuration.ModMetadataHelper;

public class ModVersionParser {
	private final Project project;

	private String version = null;

	public ModVersionParser(Project project) {
		this.project = project;
	}

	public String getModVersion() {
		if (version != null) {
			return version;
		}

		for (ModMetadataHelper helperFactory : project.getExtensions().getByType(LoomGradleExtensionAPI.class).getModMetadataHelpers().get().values()) {
			File metadata = locateModMetadata(helperFactory.getFileName());

			if (metadata == null) {
				continue;
			}

			ModMetadataHelper.Metadata helper;

			try {
				helper = helperFactory.createMetadata(metadata);
			} catch (UnsupportedOperationException ignored) {
				continue;
			}

			version = helper.getVersion();
			break;
		}

		if (version == null) {
			throw new UnsupportedOperationException("Unable to find the version in the mod metadata!");
		}

		return version;
	}

	private @Nullable File locateModMetadata(String fileName) {
		try {
			return project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets()
					.getByName("main")
					.getResources()
					.matching(patternFilterable -> patternFilterable.include(fileName))
					.getSingleFile();
		} catch (IllegalStateException e) {
			return null;
		}
	}
}
