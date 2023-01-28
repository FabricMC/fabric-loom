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

package net.fabricmc.loom.task.prod;

import java.io.File;
import java.io.IOException;

import javax.inject.Inject;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.annotations.ApiStatus;

import net.fabricmc.loom.task.AbstractLoomTask;
import net.fabricmc.loom.util.ZipUtils;

public abstract class ProdRunServerTask extends AbstractProdRunTask {
	@Nested
	public abstract Property<String> getInstallerVersion();

	@Inject
	public ProdRunServerTask() {
		final var propertiesJarTask = getProject().getTasks().register(getName() + "ServerPropertiesJar", ServerPropertiesJarTask.class, task -> {
			task.getMinecraftVersion().convention(getMinecraftVersion());
			task.getFabricLoaderVersion().convention(getFabricLoaderData().version());
			task.getOutput().set(new File(getWorkingDir(), ".fabric/server-properties.jar"));
		});

		// Add the installer server launcher and jar containing the properties to the classpath
		getClasspath().from(propertiesJarTask.map(ServerPropertiesJarTask::getOutput));
		getClasspath().from(dependencyProvider(getInstallerVersion().map("net.fabricmc:fabric-installer:%s:server"::formatted)));
	}

	// Small task to generate the install.properties used by the server launcher
	@ApiStatus.Internal
	public abstract static class ServerPropertiesJarTask extends AbstractLoomTask {
		private static final String INSTALL_PROPERTIES_NAME = "install.properties";

		@Input
		public abstract Property<String> getFabricLoaderVersion();

		@Input
		public abstract Property<String> getMinecraftVersion();

		@OutputFile
		public abstract RegularFileProperty getOutput();

		@TaskAction
		public void run() throws IOException {
			final String content = String.join(
					"\n",
					"fabric-loader-version=" + getFabricLoaderVersion().get(),
					"game-version=" + getMinecraftVersion().get()
			);

			ZipUtils.add(getOutput().get().getAsFile().toPath(), INSTALL_PROPERTIES_NAME, content);
		}
	}
}
