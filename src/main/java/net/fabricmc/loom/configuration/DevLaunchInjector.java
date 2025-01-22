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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import javax.inject.Inject;

import org.gradle.api.Project;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.artifacts.dsl.DependencyHandler;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.configuration.mods.dependency.LocalMavenHelper;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.ExceptionUtil;
import net.fabricmc.loom.util.ZipUtils;

/**
 * Add the dev launch injector to the project dependencies. This jar is extracted from loom's resources.
 */
public abstract class DevLaunchInjector implements Runnable {
	private static final String JAR_NAME = "devLaunchInjector.jar";
	private static final String DLI_GROUP = "net.fabricmc";
	private static final String DLI_NAME = "dev-launch-injector";
	private static final List<String> DLI_JAR_CONTENTS = List.of(
			"net/fabricmc/devlaunchinjector/Main.class"
	);

	@Inject
	protected abstract Project getProject();

	@Inject
	protected abstract DependencyHandler getDependencies();

	@Override
	public void run() {
		getDependencies().add(Constants.Configurations.LOOM_DEVELOPMENT_DEPENDENCIES, "%s:%s:[%s]".formatted(DLI_GROUP, DLI_NAME, LoomGradlePlugin.LOOM_VERSION));

		try {
			extractDLI();
		} catch (IOException e) {
			throw ExceptionUtil.createDescriptiveWrapper(UncheckedIOException::new, "Failed to extract dev launch injector", e);
		}
	}

	// TODO maybe look into making this a task
	private void extractDLI() throws IOException {
		LoomGradleExtension extension = LoomGradleExtension.get(getProject());
		File globalMinecraftRepo = extension.getFiles().getGlobalMinecraftRepo();
		LocalMavenHelper mavenHelper = new LocalMavenHelper(DLI_GROUP, DLI_NAME, LoomGradlePlugin.LOOM_VERSION, null, globalMinecraftRepo.toPath());
		Path output = mavenHelper.getOutputFile(null);

		if (Files.notExists(output) || extension.refreshDeps()) {
			Files.deleteIfExists(output);
			Files.createDirectories(output.getParent());

			for (String path : DLI_JAR_CONTENTS) {
				try (InputStream is = DevLaunchInjector.class.getClassLoader().getResourceAsStream(path)) {
					Objects.requireNonNull(is, "Failed to find %s in resources".formatted(path));
					ZipUtils.add(output, path, is.readAllBytes());
				} catch (Exception e) {
					throw new RuntimeException("Failed to extract dev launch injector", e);
				}
			}

			mavenHelper.savePom();
		}
	}
}
