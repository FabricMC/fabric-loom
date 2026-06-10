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
import java.io.InputStream;
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
import org.gradle.work.DisableCachingByDefault;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.RunConfiguration;
import net.fabricmc.loom.configuration.ide.DefaultRunConfigurationSettings;
import net.fabricmc.loom.configuration.ide.RunConfigUtils;
import net.fabricmc.loom.configuration.ide.idea.IdeaSyncTask;
import net.fabricmc.loom.util.Arguments;
import net.fabricmc.loom.util.Constants;

@DisableCachingByDefault
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

	private static List<EclipseRunConfig> getRunConfigs(Project project) throws IOException {
		EclipseModel eclipseModel = project.getExtensions().getByType(EclipseModel.class);
		LoomGradleExtension extension = LoomGradleExtension.get(project);

		List<EclipseRunConfig> runConfigs = new ArrayList<>();

		for (RunConfiguration settings : extension.getRunConfigs()) {
			if (!settings.getGenerateRunConfig().get()) {
				continue;
			}

			final String name = settings.getName();
			final File configs = new File(project.getProjectDir(), eclipseModel.getProject().getName() + "_" + name + ".launch");
			final RunConfiguration runConfiguration = DefaultRunConfigurationSettings.finialise(settings, project);
			final String config;

			try {
				config = fromTemplate(runConfiguration, project);
			} catch (IOException e) {
				throw new UncheckedIOException("Failed to generate Eclipse run configuration", e);
			}

			EclipseRunConfig eclipseRunConfig = project.getObjects().newInstance(EclipseRunConfig.class);
			eclipseRunConfig.getLaunchContent().set(config);
			eclipseRunConfig.getLaunchFile().set(project.file(configs));
			runConfigs.add(eclipseRunConfig);

			RunConfigUtils.createRunDirectory(settings);
		}

		return runConfigs;
	}

	private static String fromTemplate(RunConfiguration run, Project project) throws IOException {
		String dummyConfig;

		try (InputStream input = IdeaSyncTask.class.getClassLoader().getResourceAsStream("eclipse_run_config_template.xml")) {
			dummyConfig = new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}

		String runDir = RunConfigUtils.formatRunDir(run, project, File::getAbsolutePath, "${workspace_loc:%ECLIPSE_PROJECT%}/"::concat);
		String eclipseProjectName = project.getExtensions().getByType(EclipseModel.class).getProject().getName();

		dummyConfig = dummyConfig.replace("%NAME%", RunConfigUtils.getDisplayName(run, project));
		dummyConfig = dummyConfig.replace("%MAIN_CLASS%", run.getDevLaunchMainClass().get());
		dummyConfig = dummyConfig.replace("%RUN_DIRECTORY%", runDir);
		dummyConfig = dummyConfig.replace("%ECLIPSE_PROJECT%", eclipseProjectName);
		dummyConfig = dummyConfig.replace("%PROGRAM_ARGS%", Arguments.join(run.getProgramArguments().get()).replaceAll("\"", "&quot;"));
		dummyConfig = dummyConfig.replace("%VM_ARGS%", Arguments.join(run.getJvmArguments().get()).replaceAll("\"", "&quot;"));
		dummyConfig = dummyConfig.replace("%ECLIPSE_ENV_VARS%", RunConfigUtils.formatEnvVars(run, "<mapEntry key=\"%s\" value=\"%s\"/>"));

		return dummyConfig;
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
