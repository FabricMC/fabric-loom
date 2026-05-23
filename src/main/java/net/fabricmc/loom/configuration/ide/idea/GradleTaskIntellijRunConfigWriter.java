/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2026 FabricMC
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

package net.fabricmc.loom.configuration.ide.idea;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import groovy.xml.XmlUtil;
import org.gradle.api.Project;

import net.fabricmc.loom.api.RunConfiguration;
import net.fabricmc.loom.configuration.ide.RunConfigUtils;
import net.fabricmc.loom.task.LoomTasks;

public final class GradleTaskIntellijRunConfigWriter extends AbstractIntellijRunConfigWriter {
	public GradleTaskIntellijRunConfigWriter(RunConfiguration run, Project project) {
		super(run, project);
	}

	@Override
	public String render() throws IOException {
		final String xml;

		try (InputStream input = IdeaSyncTask.class.getClassLoader().getResourceAsStream("idea_gradle_run_config_template.xml")) {
			xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}

		String folderName = run.getIdeConfigFolder().getOrNull();
		String taskPath = project.getPath().equals(":")
				? ":" + LoomTasks.getRunConfigTaskName(run)
				: project.getPath() + ":" + LoomTasks.getRunConfigTaskName(run);

		return xml.replace("%NAME%", RunConfigUtils.getDisplayName(run, project))
				.replace("%TASK_NAME%", taskPath)
				.replace("%IDEA_FOLDER_NAME%", folderName == null ? "" : "folderName=\"" + XmlUtil.escapeXml(folderName) + "\"");
	}

	@Override
	public String getType() {
		return GRADLE_TASK_TYPE;
	}
}
