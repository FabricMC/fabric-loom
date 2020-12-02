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

import java.io.File;
import java.util.function.Consumer;

import com.google.common.io.Files;
import org.gradle.api.Project;

import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.DependencyProvider;
import net.fabricmc.loom.util.JarUtil;

public class ForgeUniversalProvider extends DependencyProvider {
	private File forge;
	private File forgeManifest;

	public ForgeUniversalProvider(Project project) {
		super(project);
	}

	@Override
	public void provide(DependencyInfo dependency, Consumer<Runnable> postPopulationScheduler) throws Exception {
		forge = new File(getExtension().getProjectPersistentCache(), "forge-" + dependency.getDependency().getVersion() + "-universal.jar");
		forgeManifest = new File(getExtension().getProjectPersistentCache(), "forge-" + dependency.getDependency().getVersion() + "-manifest.mf");

		if (!forge.exists() || isRefreshDeps()) {
			File dep = dependency.resolveFile().orElseThrow(() -> new RuntimeException("Could not resolve Forge"));
			Files.copy(dep, forge);
		}

		if (!forgeManifest.exists() || isRefreshDeps()) {
			JarUtil.extractFile(forge, "META-INF/MANIFEST.MF", forgeManifest);
		}
	}

	public File getForge() {
		return forge;
	}

	public File getForgeManifest() {
		return forgeManifest;
	}

	@Override
	public String getTargetConfig() {
		return Constants.Configurations.FORGE_UNIVERSAL;
	}
}
