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

package net.fabricmc.loom.configuration.ide.idea;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import groovy.xml.XmlUtil;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.project.IsolatedProject;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;
import org.jetbrains.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.RunConfiguration;
import net.fabricmc.loom.configuration.ide.DefaultRunConfigurationSettings;
import net.fabricmc.loom.configuration.ide.RunConfigUtils;
import net.fabricmc.loom.configuration.ide.RuntimeLibraries;
import net.fabricmc.loom.task.AbstractLoomTask;
import net.fabricmc.loom.util.Arguments;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.gradle.SourceSetHelper;
import net.fabricmc.loom.util.gradle.SourceSetReference;

@DisableCachingByDefault
public abstract class IdeaSyncTask extends AbstractLoomTask {
	private static final Logger LOGGER = LoggerFactory.getLogger(IdeaSyncTask.class);

	@Nested
	protected abstract ListProperty<IntellijRunConfig> getIdeaRunConfigs();

	@Inject
	public IdeaSyncTask() {
		setGroup(Constants.TaskGroup.IDE);
		getIdeaRunConfigs().set(getProject().provider(this::getRunConfigs));
	}

	@TaskAction
	public void runTask() throws IOException {
		for (IntellijRunConfig config : getIdeaRunConfigs().get()) {
			config.writeLaunchFile();
		}
	}

	// See: https://github.com/FabricMC/fabric-loom/pull/206#issuecomment-986054254 for the reason why XML's are still used to provide the run configs
	private List<IntellijRunConfig> getRunConfigs() throws IOException {
		IsolatedProject rootProject = getProject().getIsolated().getRootProject();
		LoomGradleExtension extension = LoomGradleExtension.get(getProject());
		String projectPath = getProject().getPath().equals(rootProject.getPath()) ? "" : getProject().getPath().replace(':', '_');
		File runConfigsDir = new File(rootProject.getProjectDirectory().file(".idea").getAsFile(), "runConfigurations");

		List<IntellijRunConfig> configs = new ArrayList<>();

		for (RunConfiguration settings : extension.getRunConfigs()) {
			if (!settings.getGenerateRunConfig().get()) {
				continue;
			}

			RunConfiguration runConfiguration = DefaultRunConfigurationSettings.finialise(settings, getProject());
			String name = RunConfigUtils.getDisplayName(runConfiguration, getProject()).replaceAll("[^a-zA-Z0-9$_]", "_");

			File runConfigFile = new File(runConfigsDir, name + projectPath + ".xml");
			String runConfigXml = fromTemplate(runConfiguration, getProject());
			final List<String> excludedLibraryPaths = RuntimeLibraries.getExcludedLibraryPaths(getProject(), runConfiguration);

			IntellijRunConfig irc = getProject().getObjects().newInstance(IntellijRunConfig.class);
			irc.getRunConfigXml().set(runConfigXml);
			irc.getExcludedLibraryPaths().set(excludedLibraryPaths);
			irc.getLaunchFile().set(runConfigFile);
			configs.add(irc);

			RunConfigUtils.createRunDirectory(settings);
		}

		return configs;
	}

	private static String fromTemplate(RunConfiguration run, Project project) throws IOException {
		String xml;

		try (InputStream input = IdeaSyncTask.class.getClassLoader().getResourceAsStream("idea_run_config_template.xml")) {
			xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}

		String runDir = RunConfigUtils.formatRunDir(run, project, File::getAbsolutePath, "$PROJECT_DIR$/%s"::formatted);
		String folderName = run.getIdeConfigFolder().getOrNull();
		SourceSet sourceSet = SourceSetHelper.getSourceSetByName(run.getSourceSet().get(), project);

		xml = xml.replace("%NAME%", RunConfigUtils.getDisplayName(run, project));
		xml = xml.replace("%MAIN_CLASS%", run.getDevLaunchMainClass().get());
		xml = xml.replace("%IDEA_MODULE%", IdeaUtils.getIdeaModuleName(new SourceSetReference(sourceSet, project)));
		xml = xml.replace("%RUN_DIRECTORY%", runDir);
		xml = xml.replace("%PROGRAM_ARGS%", Arguments.join(run.getProgramArguments().get()).replace("\"", "&quot;"));
		xml = xml.replace("%VM_ARGS%", Arguments.join(run.getJvmArguments().get()).replace("\"", "&quot;"));
		xml = xml.replace("%IDEA_ENV_VARS%", RunConfigUtils.formatEnvVars(run, "<env name=\"%s\" value=\"%s\"/>"));
		xml = xml.replace("%IDEA_FOLDER_NAME%", folderName == null ? "" : "folderName=\"" + XmlUtil.escapeXml(folderName) + "\"");

		return xml;
	}

	public interface IntellijRunConfig {
		@Input
		Property<String> getRunConfigXml();

		@Input
		ListProperty<String> getExcludedLibraryPaths();

		@OutputFile
		RegularFileProperty getLaunchFile();

		default void writeLaunchFile() throws IOException {
			Path launchFile = getLaunchFile().get().getAsFile().toPath();

			if (Files.notExists(launchFile)) {
				Files.createDirectories(launchFile.getParent());
				Files.writeString(launchFile, getRunConfigXml().get(), StandardCharsets.UTF_8);
			}

			try {
				setClasspathModifications(launchFile, getExcludedLibraryPaths().get());
			} catch (Exception e) {
				LOGGER.error("Failed to modify run configuration xml", e);
			}
		}
	}

	private static void setClasspathModifications(Path runConfig, List<String> exclusions) throws IOException {
		final String inputXml = Files.readString(runConfig, StandardCharsets.UTF_8);
		final String outputXml;

		try {
			outputXml = setClasspathModificationsInXml(inputXml, exclusions);
		} catch (Exception e) {
			LOGGER.error("Failed to modify idea xml", e);

			return;
		}

		if (!inputXml.equals(outputXml)) {
			Files.writeString(runConfig, outputXml, StandardCharsets.UTF_8);
		}
	}

	@VisibleForTesting
	public static String setClasspathModificationsInXml(String input, List<String> exclusions) throws Exception {
		final DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
		final DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
		final Document document = documentBuilder.parse(new InputSource(new StringReader(input)));
		final Element root = document.getDocumentElement();

		final NodeList nodeList = root.getElementsByTagName("configuration");
		assert nodeList.getLength() == 1;

		final Element configuration = (Element) nodeList.item(0);
		final NodeList classpathModificationsList = configuration.getElementsByTagName("classpathModifications");

		// Remove all the existing exclusions
		for (int i = 0; i < classpathModificationsList.getLength(); i++) {
			configuration.removeChild(classpathModificationsList.item(i));
		}

		final Element classpathModifications = document.createElement("classpathModifications");

		for (String exclusionPath : exclusions) {
			final Element exclusion = document.createElement("entry");

			exclusion.setAttribute("exclude", "true");
			exclusion.setAttribute("path", exclusionPath);

			classpathModifications.appendChild(exclusion);
		}

		configuration.appendChild(classpathModifications);

		final TransformerFactory transformerFactory = TransformerFactory.newInstance();
		final Transformer transformer = transformerFactory.newTransformer();
		transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

		final DOMSource source = new DOMSource(document);

		final StringWriter writer = new StringWriter();
		transformer.transform(source, new StreamResult(writer));

		return writer.toString().replace("\r", "");
	}
}
