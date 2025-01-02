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

package net.fabricmc.loom.task.prod;

import java.io.File;

import javax.inject.Inject;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.process.ExecSpec;
import org.jetbrains.annotations.ApiStatus;

import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.Platform;

/**
 * A task that runs the Minecraft client in a similar way to a production launcher. You must manually register a task of this type to use it.
 */
@ApiStatus.Experimental
public abstract non-sealed class ClientProductionRunTask extends AbstractProductionRunTask {
	// Internal options
	@Input
	protected abstract Property<String> getAssetsIndex();

	@InputFiles
	protected abstract DirectoryProperty getAssetsDir();

	@Inject
	public ClientProductionRunTask() {
		getAssetsIndex().set(getExtension().getMinecraftVersion()
				.map(minecraftVersion -> getExtension()
						.getMinecraftProvider()
						.getVersionInfo()
						.assetIndex()
						.fabricId(minecraftVersion)
				)
		);
		getAssetsDir().set(new File(getExtension().getFiles().getUserCache(), "assets"));
		getMainClass().convention("net.fabricmc.loader.impl.launch.knot.KnotClient");

		getClasspath().from(getExtension().getMinecraftProvider().getMinecraftClientJar());
		getClasspath().from(detachedConfigurationProvider("net.fabricmc:fabric-loader:%s", getProjectLoaderVersion()));
		getClasspath().from(detachedConfigurationProvider("net.fabricmc:intermediary:%s", getExtension().getMinecraftVersion()));
		getClasspath().from(getProject().getConfigurations().named(Constants.Configurations.MINECRAFT_TEST_CLIENT_RUNTIME_LIBRARIES));

		dependsOn("downloadAssets");
	}

	@Override
	protected void configureJvmArgs(ExecSpec exec) {
		super.configureJvmArgs(exec);

		if (Platform.CURRENT.getOperatingSystem().isMacOS()) {
			exec.args("-XstartOnFirstThread");
		}
	}

	@Override
	protected void configureProgramArgs(ExecSpec exec) {
		super.configureProgramArgs(exec);

		exec.args(
				"--assetIndex", getAssetsIndex().get(),
				"--assetsDir", getAssetsDir().get().getAsFile().getAbsolutePath(),
				"--gameDir", getRunDir().get().getAsFile().getAbsolutePath()
		);
	}
}
