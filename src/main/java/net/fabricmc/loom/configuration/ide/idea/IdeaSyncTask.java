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
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.inject.Inject;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.project.IsolatedProject;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;
import org.jetbrains.annotations.VisibleForTesting;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import net.fabricmc.loom.api.RunConfiguration;
import net.fabricmc.loom.configuration.ide.DefaultRunConfigurationSettings;
import net.fabricmc.loom.configuration.ide.RunConfigUtils;
import net.fabricmc.loom.configuration.ide.RuntimeLibraries;
import net.fabricmc.loom.task.AbstractLoomTask;
import net.fabricmc.loom.util.Constants;

@DisableCachingByDefault
public abstract class IdeaSyncTask extends AbstractLoomTask {
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

	private List<IntellijRunConfig> getRunConfigs() throws IOException {
		return getRunConfigs(getProject(), getExtension().getRunConfigs());
	}

	@VisibleForTesting
	public static List<IntellijRunConfig> getRunConfigs(Project project, Collection<? extends RunConfiguration> runs) throws IOException {
		IsolatedProject rootProject = project.getIsolated().getRootProject();
		String projectPath = project.getPath().equals(rootProject.getPath()) ? "" : project.getPath().replace(':', '_');
		File runConfigsDir = new File(rootProject.getProjectDirectory().file(".idea").getAsFile(), "runConfigurations");

		List<IntellijRunConfig> configs = new ArrayList<>();

		for (RunConfiguration settings : runs) {
			if (!settings.getGenerateRunConfig().get()) {
				continue;
			}

			RunConfiguration runConfiguration = DefaultRunConfigurationSettings.finialise(settings, project);
			String name = RunConfigUtils.getDisplayName(runConfiguration, project).replaceAll("[^a-zA-Z0-9$_]", "_");
			IntellijRunConfigWriter writer = createWriter(runConfiguration, project);

			File runConfigFile = new File(runConfigsDir, name + projectPath + ".xml");
			String runConfigXml = writer.render();

			IntellijRunConfig irc = project.getObjects().newInstance(IntellijRunConfig.class);
			irc.getRunConfigXml().set(runConfigXml);
			irc.getRunConfigType().set(writer.getType());
			irc.getLaunchFile().set(runConfigFile);
			configs.add(irc);

			RunConfigUtils.createRunDirectory(settings);
		}

		return configs;
	}

	private static IntellijRunConfigWriter createWriter(RunConfiguration run, Project project) {
		if (run.getPreferGradleTask().get()) {
			return new GradleTaskIntellijRunConfigWriter(run, project);
		}

		return new ApplicationIntellijRunConfigWriter(run, project, RuntimeLibraries.getExcludedLibraryPaths(project, run));
	}

	public interface IntellijRunConfig {
		@Input
		Property<String> getRunConfigXml();

		@Input
		Property<String> getRunConfigType();

		@OutputFile
		RegularFileProperty getLaunchFile();

		default void writeLaunchFile() throws IOException {
			Path launchFile = getLaunchFile().get().getAsFile().toPath();
			final String expectedXml = getRunConfigXml().get();

			if (Files.notExists(launchFile)) {
				Files.createDirectories(launchFile.getParent());
				Files.writeString(launchFile, expectedXml, StandardCharsets.UTF_8);
			} else {
				final String existingXml = Files.readString(launchFile, StandardCharsets.UTF_8);

				if (shouldOverwrite(existingXml)) {
					backupLaunchFile(launchFile);
					Files.writeString(launchFile, expectedXml, StandardCharsets.UTF_8);
				} else if (IntellijRunConfigWriter.APPLICATION_TYPE.equals(getRunConfigType().get())) {
					try {
						Files.writeString(launchFile, setClasspathModificationsInXml(existingXml, expectedXml), StandardCharsets.UTF_8);
					} catch (Exception e) {
						throw new IOException("Failed to update IntelliJ classpath modifications", e);
					}
				}
			}
		}

		private void backupLaunchFile(Path launchFile) throws IOException {
			final Path backupDir = launchFile.getParent().resolve("backups");
			Files.createDirectories(backupDir);
			final Path backupFile = resolveBackupPath(backupDir, launchFile.getFileName().toString());
			Files.copy(launchFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
		}

		private boolean shouldOverwrite(String existingXml) {
			return !getRunConfigType().get().equals(IntellijRunConfigWriter.getType(existingXml));
		}
	}

	private static Path resolveBackupPath(Path backupDir, String fileName) {
		Path backupPath = backupDir.resolve(fileName + ".backup");

		if (Files.notExists(backupPath)) {
			return backupPath;
		}

		int index = 1;

		while (true) {
			backupPath = backupDir.resolve(fileName + "." + index + ".backup");

			if (Files.notExists(backupPath)) {
				return backupPath;
			}

			index++;
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

	@VisibleForTesting
	public static String setClasspathModificationsInXml(String input, String source) throws Exception {
		final DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
		final DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
		final Document inputDocument = documentBuilder.parse(new InputSource(new StringReader(input)));
		final Document sourceDocument = documentBuilder.parse(new InputSource(new StringReader(source)));

		final Element inputConfiguration = getConfigurationElement(inputDocument);
		final Element sourceConfiguration = getConfigurationElement(sourceDocument);
		final NodeList sourceClasspathModificationsList = sourceConfiguration.getElementsByTagName("classpathModifications");

		removeClasspathModifications(inputConfiguration);

		if (sourceClasspathModificationsList.getLength() > 0) {
			inputConfiguration.appendChild(inputDocument.importNode(sourceClasspathModificationsList.item(0), true));
		}

		return documentToString(inputDocument);
	}

	private static Element getConfigurationElement(Document document) {
		final NodeList nodeList = document.getDocumentElement().getElementsByTagName("configuration");
		assert nodeList.getLength() == 1;
		return (Element) nodeList.item(0);
	}

	private static void removeClasspathModifications(Element configuration) {
		final NodeList classpathModificationsList = configuration.getElementsByTagName("classpathModifications");

		for (int i = classpathModificationsList.getLength() - 1; i >= 0; i--) {
			configuration.removeChild(classpathModificationsList.item(i));
		}
	}

	private static String documentToString(Document document) throws Exception {
		final TransformerFactory transformerFactory = TransformerFactory.newInstance();
		final Transformer transformer = transformerFactory.newTransformer();
		transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

		final DOMSource source = new DOMSource(document);
		final StringWriter writer = new StringWriter();
		transformer.transform(source, new StreamResult(writer));

		return writer.toString().replace("\r", "");
	}
}
