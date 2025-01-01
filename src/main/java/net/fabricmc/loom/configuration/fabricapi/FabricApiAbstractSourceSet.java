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

package net.fabricmc.loom.configuration.fabricapi;

import java.io.IOException;

import javax.inject.Inject;

import org.gradle.api.Project;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftSourceSets;
import net.fabricmc.loom.util.fmj.FabricModJson;
import net.fabricmc.loom.util.fmj.FabricModJsonFactory;
import net.fabricmc.loom.util.gradle.SourceSetHelper;

abstract class FabricApiAbstractSourceSet {
	@Inject
	protected abstract Project getProject();

	protected abstract String getSourceSetName();

	protected void configureSourceSet(Property<String> modId, boolean isClient) {
		final LoomGradleExtension extension = LoomGradleExtension.get(getProject());
		final SourceSet mainSourceSet = SourceSetHelper.getMainSourceSet(getProject());

		final boolean isClientAndSplit = extension.areEnvironmentSourceSetsSplit() && isClient;

		SourceSetContainer sourceSets = SourceSetHelper.getSourceSets(getProject());

		// Create the new datagen sourceset, depend on the main or client sourceset.
		SourceSet dataGenSourceSet = sourceSets.create(getSourceSetName(), sourceSet -> {
			dependsOn(sourceSet, mainSourceSet);

			if (isClientAndSplit) {
				dependsOn(sourceSet, SourceSetHelper.getSourceSetByName(MinecraftSourceSets.Split.CLIENT_ONLY_SOURCE_SET_NAME, getProject()));
			}
		});

		modId.convention(getProject().provider(() -> {
			try {
				final FabricModJson fabricModJson = FabricModJsonFactory.createFromSourceSetsNullable(getProject(), dataGenSourceSet);

				if (fabricModJson == null) {
					throw new RuntimeException("Could not find a fabric.mod.json file in the data source set or a value for DataGenerationSettings.getModId()");
				}

				return fabricModJson.getId();
			} catch (IOException e) {
				throw new org.gradle.api.UncheckedIOException("Failed to read mod id from the datagen source set.", e);
			}
		}));

		extension.getMods().create(modId.get(), mod -> {
			// Create a classpath group for this mod. Assume that the main sourceset is already in a group.
			mod.sourceSet(getSourceSetName());
		});

		extension.createRemapConfigurations(sourceSets.getByName(getSourceSetName()));
	}

	private static void extendsFrom(Project project, String name, String extendsFrom) {
		final ConfigurationContainer configurations = project.getConfigurations();

		configurations.named(name, configuration -> {
			configuration.extendsFrom(configurations.getByName(extendsFrom));
		});
	}

	private void dependsOn(SourceSet sourceSet, SourceSet other) {
		sourceSet.setCompileClasspath(
				sourceSet.getCompileClasspath()
						.plus(other.getOutput())
		);

		sourceSet.setRuntimeClasspath(
				sourceSet.getRuntimeClasspath()
						.plus(other.getOutput())
		);

		extendsFrom(getProject(), sourceSet.getCompileClasspathConfigurationName(), other.getCompileClasspathConfigurationName());
		extendsFrom(getProject(), sourceSet.getRuntimeClasspathConfigurationName(), other.getRuntimeClasspathConfigurationName());
	}
}
