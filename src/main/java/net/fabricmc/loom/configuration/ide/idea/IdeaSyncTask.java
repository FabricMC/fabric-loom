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
import java.util.List;

import javax.inject.Inject;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.io.FileUtils;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.annotations.VisibleForTesting;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.ide.RunConfig;
import net.fabricmc.loom.configuration.ide.RunConfigSettings;
import net.fabricmc.loom.task.AbstractLoomTask;

public abstract class IdeaSyncTask extends AbstractLoomTask {
	@Inject
	public IdeaSyncTask() {
		// Always re-run this task.
		getOutputs().upToDateWhen(element -> false);
	}

	@TaskAction
	public void runTask() throws IOException {
		File projectDir = getProject().getRootProject().file(".idea");
		projectDir.mkdirs();

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

			final List<String> excludedLibraryPaths = config.getExcludedLibraryPaths(getProject());

			try {
				setClasspathModifications(runConfigs.toPath(), excludedLibraryPaths);
			} catch (Exception e) {
				getProject().getLogger().error("Failed to modify run configuration xml", e);
			}
		}
	}

	private void setClasspathModifications(Path runConfig, List<String> exclusions) throws IOException {
		if (!IdeaUtils.supportsCustomizableClasspath()) {
			return;
		}

		final String inputXml = Files.readString(runConfig, StandardCharsets.UTF_8);
		final String outputXml;

		try {
			outputXml = setClasspathModificationsInXml(inputXml, exclusions);
		} catch (Exception e) {
			getLogger().error("Failed to modify idea xml", e);

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
