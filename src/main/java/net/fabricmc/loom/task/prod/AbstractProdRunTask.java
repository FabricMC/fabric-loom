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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.jetbrains.annotations.ApiStatus;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.InstallerData;
import net.fabricmc.loom.task.AbstractLoomJavaExecTask;

public abstract class AbstractProdRunTask extends AbstractLoomJavaExecTask {
	@Input
	public abstract ConfigurableFileCollection getMods();

	@Inject
	public AbstractProdRunTask() {
		setWorkingDir(getProject().file("run"));
	}

	@Override
	public void exec() {
		getWorkingDir().mkdirs();
		super.exec();
	}

	@Override
	public List<String> getJvmArgs() {
		List<String> jvmArgs = new ArrayList<>(super.getJvmArgs());

		final String mods = getMods().getFiles().stream()
				.map(File::getAbsolutePath)
				.collect(Collectors.joining(File.pathSeparator));

		// TODO ideally needs to go in the args file, AbstractLoomJavaExecTask needs a bit of refactoring
		jvmArgs.add("-Dfabric.addMods=" + mods);

		return jvmArgs;
	}

	@Internal
	protected Provider<FileCollection> dependencyProvider(Provider<String> dependencyNotationProvider) {
		return getProject().provider(() -> {
			return getProject().getConfigurations().detachedConfiguration(
					getProject().getDependencies().create(dependencyNotationProvider.get())
			);
		});
	}

	@Internal
	@ApiStatus.Internal
	protected String getMinecraftVersion() {
		return LoomGradleExtension.get(getProject()).getMinecraftProvider().minecraftVersion();
	}

	@Internal
	protected InstallerData getFabricLoaderData() {
		return LoomGradleExtension.get(getProject()).getInstallerData();
	}
}
