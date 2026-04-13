/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2021 FabricMC
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

package net.fabricmc.loom.configuration.ide;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.gradle.api.Project;
import org.gradle.api.tasks.SourceSet;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;

import net.fabricmc.loom.api.RunConfiguration;
import net.fabricmc.loom.configuration.ide.idea.IdeaUtils;
import net.fabricmc.loom.util.gradle.SourceSetHelper;
import net.fabricmc.loom.util.gradle.SourceSetReference;

public class RunConfig {
	public final RunConfiguration runConfiguration;

	private final String configName;
	public String eclipseProjectName;
	public String ideaModuleName;
	private final String mainClass;
	public String runDirIdeaUrl;
	public File runDir;
	public List<String> vmArgs = new ArrayList<>();
	public List<String> programArgs = new ArrayList<>();
	public Map<String, Object> environmentVariables;
	public String projectName;
	public String folderName;

	public RunConfig(RunConfiguration runConfiguration, Project project) {
		this.runConfiguration = runConfiguration;
		configName = RunConfigUtils.getDisplayName(runConfiguration, project);
		mainClass = runConfiguration.getDevLaunchMainClass().get();
	}

	public static RunConfig runConfig(Project project, RunConfiguration settings) {
		DefaultRunConfigurationSettings.finialise(settings, project);
		settings = RunConfigUtils.toSerialisable(settings, project);

		SourceSet sourceSet = SourceSetHelper.getSourceSetByName(settings.getSourceSet().get(), project);
		File runDir = settings.getRunDirectory().get().getAsFile();

		RunConfig runConfig = new RunConfig(settings, project);

		runConfig.eclipseProjectName = project.getExtensions().getByType(EclipseModel.class).getProject().getName();
		runConfig.ideaModuleName = IdeaUtils.getIdeaModuleName(new SourceSetReference(sourceSet, project));
		runConfig.runDirIdeaUrl = "file://$PROJECT_DIR$/" + runDir; // TODO check if the runDir is relative to the project root
		runConfig.runDir = runDir;

		// Custom parameters
		runConfig.programArgs.addAll(settings.getProgramArguments().get());
		runConfig.vmArgs.addAll(settings.getJvmArguments().get());
		runConfig.environmentVariables = new HashMap<>();
		runConfig.environmentVariables.putAll(settings.getEnvironmentVars().get());
		runConfig.projectName = project.getName();
		runConfig.folderName = settings.getIdeConfigFolder().getOrNull();

		return runConfig;
	}
}
