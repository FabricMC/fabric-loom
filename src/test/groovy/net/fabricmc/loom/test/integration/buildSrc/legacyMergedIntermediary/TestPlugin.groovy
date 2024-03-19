/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022 FabricMC
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

package net.fabricmc.loom.test.integration.buildSrc.legacyMergedIntermediary

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property

import net.fabricmc.loom.api.LoomGradleExtensionAPI
import net.fabricmc.loom.api.mappings.intermediate.IntermediateMappingsProvider
import net.fabricmc.loom.util.ZipUtils

class TestPlugin implements Plugin<Project> {
	@Override
	void apply(Project project) {
		LoomGradleExtensionAPI extension = project.getExtensions().getByName("loom")
		extension.setIntermediateMappingsProvider(LegacyIntermediaryProvider.class) {
			mappingPath.set(project.property("loom.test.legacyMergedIntermediary.mappingPath"))
		}
	}

	abstract static class LegacyIntermediaryProvider extends IntermediateMappingsProvider {
		final String name = "legacyMerged"

		abstract Property<String> getMappingPath();

		@Override
		void provide(Path tinyMappings) throws IOException {
			if (getMinecraftVersion().get() != "1.2.5") {
				throw new IllegalStateException("This plugin only supports Minecraft 1.2.5")
			}

			byte[] data = ZipUtils.unpack(Paths.get(getMappingPath().get()), "1.2.5-intermediary.tiny")
			Files.write(tinyMappings, data)
		}
	}
}
