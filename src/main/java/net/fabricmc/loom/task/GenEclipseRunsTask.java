/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2024 FabricMC
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

package net.fabricmc.loom.task;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.ide.RunConfig;
import net.fabricmc.loom.configuration.ide.RunConfigSettings;
import net.fabricmc.loom.util.Constants;

public abstract class GenEclipseRunsTask extends AbstractLoomTask {
	@Nested
	protected abstract ListProperty<EclipseRunConfig> getEclipseRunConfigs();

	@Inject
	public GenEclipseRunsTask() {
		setGroup(Constants.TaskGroup.IDE);
		getEclipseRunConfigs().set(getProject().provider(() -> getRunConfigs(getProject())));
	}

	@TaskAction
	public void genRuns() throws IOException {
		for (EclipseRunConfig runConfig : getEclipseRunConfigs().get()) {
			runConfig.writeLaunchFile();
		}
	}

	private static List<EclipseRunConfig> getRunConfigs(Project project) {
		EclipseModel eclipseModel = project.getExtensions().getByType(EclipseModel.class);
		LoomGradleExtension extension = LoomGradleExtension.get(project);

		List<EclipseRunConfig> runConfigs = new ArrayList<>();

		for (RunConfigSettings settings : extension.getRunConfigs()) {
			if (!settings.isIdeConfigGenerated()) {
				continue;
			}

			final String name = settings.getName();
			final File configs = new File(project.getProjectDir(), eclipseModel.getProject().getName() + "_" + name + ".launch");
			final RunConfig configInst = RunConfig.runConfig(project, settings);
			final String config;

			try {
				config = configInst.fromDummy("eclipse_run_config_template.xml", false, project);
			} catch (IOException e) {
				throw new UncheckedIOException("Failed to generate Eclipse run configuration", e);
			}

			EclipseRunConfig eclipseRunConfig = project.getObjects().newInstance(EclipseRunConfig.class);
			eclipseRunConfig.getLaunchContent().set(config);
			eclipseRunConfig.getLaunchFile().set(project.file(configs));
			runConfigs.add(eclipseRunConfig);

			settings.makeRunDir();
		}

		return runConfigs;
	}

	public interface EclipseRunConfig {
		@Input
		Property<String> getLaunchContent();

		@OutputFile
		RegularFileProperty getLaunchFile();

		default void writeLaunchFile() throws IOException {
			Path launchFile = getLaunchFile().get().getAsFile().toPath();

			if (Files.notExists(launchFile)) {
				Files.writeString(launchFile, getLaunchContent().get(), StandardCharsets.UTF_8);
			}
		}
	}
}
