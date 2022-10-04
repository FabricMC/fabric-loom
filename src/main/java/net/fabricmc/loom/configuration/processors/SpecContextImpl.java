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

package net.fabricmc.loom.configuration.processors;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.gradle.api.Project;
import org.gradle.api.tasks.SourceSet;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.RemapConfigurationSettings;
import net.fabricmc.loom.api.processor.SpecContext;
import net.fabricmc.loom.util.fmj.FabricModJson;
import net.fabricmc.loom.util.fmj.FabricModJsonFactory;
import net.fabricmc.loom.util.gradle.SourceSetHelper;

/**
 * @param modDependencies External mods that are depended on
 * @param localMods The main mod being built. In the future this may also include other mods.
 */
public record SpecContextImpl(List<FabricModJson> modDependencies, List<FabricModJson> localMods) implements SpecContext {
	public static SpecContextImpl create(Project project) {
		return new SpecContextImpl(getDependentMods(project), getMods(project));
	}

	private static List<FabricModJson> getDependentMods(Project project) {
		final LoomGradleExtension extension = LoomGradleExtension.get(project);
		var mods = new ArrayList<FabricModJson>();

		for (RemapConfigurationSettings entry : extension.getRemapConfigurations()) {
			final Set<File> artifacts = entry.getSourceConfiguration().get().resolve();

			for (File artifact : artifacts) {
				final FabricModJson fabricModJson;

				try {
					fabricModJson = FabricModJsonFactory.createFromZipNullable(artifact.toPath());
				} catch (IOException e) {
					throw new UncheckedIOException("Failed to read dependent mod jar: " + artifact, e);
				}

				if (fabricModJson != null) {
					mods.add(fabricModJson);
				}
			}
		}

		// TODO supporting projects here should magically allow TAWs and what not to work across project deps :)

		return sorted(mods);
	}

	private static List<FabricModJson> getMods(Project project) {
		final LoomGradleExtension extension = LoomGradleExtension.get(project);
		var sourceSets = new ArrayList<SourceSet>();
		sourceSets.add(SourceSetHelper.getMainSourceSet(project));

		if (extension.areEnvironmentSourceSetsSplit()) {
			sourceSets.add(SourceSetHelper.getSourceSetByName("client", project));
		}

		try {
			final FabricModJson fabricModJson = FabricModJsonFactory.createFromSourceSetsNullable(sourceSets.toArray(SourceSet[]::new));

			if (fabricModJson != null) {
				return List.of(fabricModJson);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		return Collections.emptyList();
	}

	// Sort to ensure stable caching
	private static List<FabricModJson> sorted(List<FabricModJson> mods) {
		return mods.stream().sorted(Comparator.comparing(FabricModJson::getId)).toList();
	}
}
