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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import groovy.xml.XmlUtil;
import org.gradle.api.Project;
import org.gradle.api.tasks.SourceSet;

import net.fabricmc.loom.api.RunConfiguration;
import net.fabricmc.loom.configuration.ide.RunConfigUtils;
import net.fabricmc.loom.util.Arguments;
import net.fabricmc.loom.util.gradle.SourceSetHelper;
import net.fabricmc.loom.util.gradle.SourceSetReference;

public final class ApplicationIntellijRunConfigWriter extends AbstractIntellijRunConfigWriter {
	private final List<String> excludedLibraryPaths;

	public ApplicationIntellijRunConfigWriter(RunConfiguration run, Project project, List<String> excludedLibraryPaths) {
		super(run, project);
		this.excludedLibraryPaths = excludedLibraryPaths;
	}

	@Override
	public String render() throws IOException {
		final String xml;

		try (InputStream input = IdeaSyncTask.class.getClassLoader().getResourceAsStream("idea_run_config_template.xml")) {
			xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}

		String runDir = RunConfigUtils.formatRunDir(run, project, File::getAbsolutePath, "$PROJECT_DIR$/%s"::formatted);
		String folderName = run.getIdeConfigFolder().getOrNull();
		SourceSet sourceSet = SourceSetHelper.getSourceSetByName(run.getSourceSet().get(), project);

		final String runConfigXml = xml.replace("%NAME%", RunConfigUtils.getDisplayName(run, project))
				.replace("%MAIN_CLASS%", run.getDevLaunchMainClass().get())
				.replace("%IDEA_MODULE%", IdeaUtils.getIdeaModuleName(new SourceSetReference(sourceSet, project)))
				.replace("%RUN_DIRECTORY%", runDir)
				.replace("%PROGRAM_ARGS%", Arguments.join(run.getProgramArguments().get()).replace("\"", "&quot;"))
				.replace("%VM_ARGS%", Arguments.join(run.getJvmArguments().get()).replace("\"", "&quot;"))
				.replace("%IDEA_ENV_VARS%", RunConfigUtils.formatEnvVars(run, "<env name=\"%s\" value=\"%s\"/>"))
				.replace("%IDEA_FOLDER_NAME%", folderName == null ? "" : "folderName=\"" + XmlUtil.escapeXml(folderName) + "\"")
				.replaceAll("(?m)^[ \\t]+$", "");

		try {
			return IdeaSyncTask.setClasspathModificationsInXml(runConfigXml, excludedLibraryPaths);
		} catch (Exception e) {
			throw new IOException("Failed to apply IntelliJ classpath modifications", e);
		}
	}

	@Override
	public String getType() {
		return APPLICATION_TYPE;
	}
}
