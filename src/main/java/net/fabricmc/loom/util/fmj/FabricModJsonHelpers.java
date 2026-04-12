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

package net.fabricmc.loom.util.fmj;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;

import net.fabricmc.loom.api.LoomGradleExtensionAPI;
import net.fabricmc.loom.util.gradle.SourceSetHelper;
import net.fabricmc.loom.LoomGradleExtension;

public class FabricModJsonHelpers {
	/**
	 * Returns the list of mods provided by either {@link LoomGradleExtensionAPI#getFabricModJsonPath()}
	 * or {@code fabric.mod.json} in main or client resources.
	 */
	public static List<FabricModJson> getModsInProject(Project project) {
		final LoomGradleExtension extension = LoomGradleExtension.get(project);
		Provider<File> overrideFile = extension.getFabricModJsonPath().getAsFile();

		if (overrideFile.isPresent()) {
			return List.of(FabricModJsonFactory.createFromFile(overrideFile.get()));
		}

		var sourceSets = new ArrayList<SourceSet>();
		sourceSets.add(SourceSetHelper.getMainSourceSet(project));

		if (extension.areEnvironmentSourceSetsSplit()) {
			sourceSets.add(SourceSetHelper.getSourceSetByName("client", project));
		}

		final FabricModJson fabricModJson = FabricModJsonFactory.createFromSourceSetsNullable(project, sourceSets.toArray(SourceSet[]::new));

		if (fabricModJson != null) {
			return List.of(fabricModJson);
		}

		return Collections.emptyList();
	}
}
