/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016, 2017, 2018 FabricMC
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

package net.fabricmc.loom.providers;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.DependencyProvider;
import net.fabricmc.loom.util.ModProcessor;
import org.gradle.api.Project;

import java.io.File;

public class ModRemapperProvider extends DependencyProvider {
	@Override
	public void provide(DependencyInfo dependency, Project project, LoomGradleExtension extension) {
		File input = dependency.resolveFile();

		project.getLogger().lifecycle("Providing " + dependency.getDepString());

		PomfProvider pomfProvider = getDependencyManager().getProvider(PomfProvider.class);

		String outputName = input.getName().substring(0, input.getName().length() - 4) + "-mapped-" + pomfProvider.pomfVersion + ".jar";//TODO use the hash of the input file or something?
		File output = new File(Constants.REMAPPED_MODS_STORE, outputName);
		if(output.exists()){
			output.delete();
		}

		ModProcessor.handleMod(input, output, project);

		if(!output.exists()){
			throw new RuntimeException("Failed to remap mod");
		}

		addDependency(output, project);
	}

	@Override
	public String getTargetConfig() {
		return Constants.COMPILE_MODS;
	}
}
