/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021 FabricMC
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

package net.fabricmc.loom.configuration.ide.intelij;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskAction;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.ide.RunConfig;
import net.fabricmc.loom.configuration.ide.RunConfigSettings;
import net.fabricmc.loom.task.AbstractLoomTask;

public abstract class IntelijSyncTask extends AbstractLoomTask {
	@Inject
	public IntelijSyncTask() {
		// Always re-run this task.
		getOutputs().upToDateWhen(element -> false);
	}

	@TaskAction
	public void runTask() throws IOException {
		File projectDir = getProject().getRootProject().file(".idea");

		if (!projectDir.exists()) {
			throw new RuntimeException("No .idea directory found");
		}

		generateRunConfigs();
	}

	// See: https://github.com/FabricMC/fabric-loom/pull/206#issuecomment-986054254 for the reason why XML's are still used to provide the run configs
	private void generateRunConfigs() throws IOException {
		Project rootProject = getProject().getRootProject();
		LoomGradleExtension extension = LoomGradleExtension.get(getProject());
		String projectPath = getProject() == rootProject ? "" : getProject().getPath().replace(':', '_');
		File runConfigsDir = new File(rootProject.file(".idea"), "runConfigurations");

		if (!runConfigsDir.exists()) {
			runConfigsDir.mkdirs();
		}

		for (RunConfigSettings settings : extension.getRunConfigs()) {
			if (!settings.isIdeConfigGenerated()) {
				continue;
			}

			RunConfig config = RunConfig.runConfig(getProject(), settings);
			String name = config.configName.replaceAll("[^a-zA-Z0-9$_]", "_");

			File runConfigs = new File(runConfigsDir, name + projectPath + ".xml");
			String runConfigXml = config.fromDummy("idea_run_config_template.xml", true, getProject());

			if (!runConfigs.exists()) {
				FileUtils.writeStringToFile(runConfigs, runConfigXml, StandardCharsets.UTF_8);
			}

			settings.makeRunDir();
		}
	}
}
