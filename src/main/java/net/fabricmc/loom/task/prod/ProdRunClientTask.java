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

import javax.inject.Inject;

import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.file.ConfigurableFileCollection;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.InstallerData;
import net.fabricmc.loom.util.Constants;

public abstract class ProdRunClientTask extends AbstractProdRunTask {
	@Inject
	public ProdRunClientTask() {
		super();
		dependsOn("configureClientLaunch");
		final ConfigurationContainer configurations = getProject().getConfigurations();

		getClasspath().from(getFabricLoaderData().artifact().path().toFile());
		getClasspath().from(configurations.named(Constants.Configurations.MINECRAFT_DEPENDENCIES));
		getClasspath().from(configurations.named(Constants.Configurations.MINECRAFT_RUNTIME_DEPENDENCIES));
		getClasspath().from(configurations.named(Constants.Configurations.LOADER_DEPENDENCIES));
		// TODO this wont use any custom intermediary versions
		getClasspath().from(configurations.detachedConfiguration(
				getProject().getDependencies().create("net.fabrimc:intermediary:" + getMinecraftVersion())
		));
	}

	@Override
	public void exec() {
		final LoomGradleExtension extension = LoomGradleExtension.get(getProject());

		getMainClass().convention(getFabricLoaderData().mainClass(InstallerData.Environment.CLIENT));
		// TODO dont duplicate this with the DLI config?
		args("--assetIndex", extension.getMinecraftProvider().getAssetIndex());
		args("--assetsDir", extension.getMinecraftProvider().getAssetIndex());
		args("--gameDir", getWorkingDir());

		final ConfigurableFileCollection classpath = getClasspath();

		classpath.from(extension.getMinecraftProvider().getMinecraftClientJar());

		super.exec();
	}
}
