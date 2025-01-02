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
import java.io.IOException;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.jetbrains.annotations.ApiStatus;

import net.fabricmc.loom.util.LoomVersions;
import net.fabricmc.loom.util.ZipUtils;

/**
 * A task that runs the server using the production server launcher. You must manually register a task of this type to use it.
 */
@ApiStatus.Experimental
public abstract non-sealed class ServerProductionRunTask extends AbstractProductionRunTask {
	/**
	 * The version of Fabric Loader to use.
	 *
	 * <p>Defaults to the version of Fabric Loader that the project is using.
	 */
	@Input
	public abstract Property<String> getLoaderVersion();

	/**
	 * The version of Minecraft to use.
	 *
	 * <p>Defaults to the version of Minecraft that the project is using.
	 */
	@Input
	public abstract Property<String> getMinecraftVersion();

	/**
	 * The version of the Fabric Installer to use.
	 *
	 * <p>Defaults to a version provided by Loom.
	 */
	@Input
	public abstract Property<String> getInstallerVersion();

	// Internal options

	@ApiStatus.Internal
	@OutputFile
	public abstract RegularFileProperty getInstallPropertiesJar();

	@Inject
	public ServerProductionRunTask() {
		getLoaderVersion().convention(getProjectLoaderVersion());
		getMinecraftVersion().convention(getExtension().getMinecraftVersion());
		getInstallPropertiesJar().convention(getProject().getLayout().getBuildDirectory().file("server_properties.jar"));
		getInstallerVersion().convention(LoomVersions.FABRIC_INSTALLER.version());

		getMainClass().convention("net.fabricmc.installer.ServerLauncher");
		getClasspath().from(detachedConfigurationProvider("net.fabricmc:fabric-installer:%s:server", getInstallerVersion()));

		getProgramArgs().add("nogui");
	}

	@Override
	public void run() throws IOException {
		ZipUtils.add(
				getInstallPropertiesJar().get().getAsFile().toPath(),
				"install.properties",
				"fabric-loader-version=%s\ngame-version=%s".formatted(getLoaderVersion().get(), getMinecraftVersion().get())
		);

		super.run();
	}

	@Override
	protected Stream<File> streamClasspath() {
		return Stream.concat(
			super.streamClasspath(),
			Stream.of(getInstallPropertiesJar().get().getAsFile())
		);
	}
}
