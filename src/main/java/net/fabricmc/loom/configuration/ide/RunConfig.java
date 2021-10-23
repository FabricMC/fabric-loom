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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.gradle.api.Project;
import org.gradle.api.tasks.SourceSet;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.InstallerData;

public class RunConfig {
	public String configName;
	public String eclipseProjectName;
	public String ideaModuleName;
	public String mainClass;
	public String runDirIdeaUrl;
	public String runDir;
	public List<String> vmArgs = new ArrayList<>();
	public List<String> programArgs = new ArrayList<>();
	public SourceSet sourceSet;

	public Element genRuns(Element doc) {
		Element root = this.addXml(doc, "component", ImmutableMap.of("name", "ProjectRunConfigurationManager"));
		root = addXml(root, "configuration", ImmutableMap.of("default", "false", "name", configName, "type", "Application", "factoryName", "Application"));

		this.addXml(root, "module", ImmutableMap.of("name", ideaModuleName));
		this.addXml(root, "option", ImmutableMap.of("name", "MAIN_CLASS_NAME", "value", mainClass));
		this.addXml(root, "option", ImmutableMap.of("name", "WORKING_DIRECTORY", "value", runDirIdeaUrl));

		if (!vmArgs.isEmpty()) {
			this.addXml(root, "option", ImmutableMap.of("name", "VM_PARAMETERS", "value", joinArguments(vmArgs)));
		}

		if (!programArgs.isEmpty()) {
			this.addXml(root, "option", ImmutableMap.of("name", "PROGRAM_PARAMETERS", "value", joinArguments(programArgs)));
		}

		return root;
	}

	public Element addXml(Node parent, String name, Map<String, String> values) {
		Document doc = parent.getOwnerDocument();

		if (doc == null) {
			doc = (Document) parent;
		}

		Element e = doc.createElement(name);

		for (Map.Entry<String, String> entry : values.entrySet()) {
			e.setAttribute(entry.getKey(), entry.getValue());
		}

		parent.appendChild(e);
		return e;
	}

	private static String getIdeaModuleName(Project project, SourceSet srcs) {
		String module = project.getName() + "." + srcs.getName();

		while ((project = project.getParent()) != null) {
			module = project.getName() + "." + module;
		}

		return module;
	}

	private static void populate(Project project, LoomGradleExtension extension, RunConfig runConfig, String environment) {
		runConfig.configName += extension.isRootProject() ? "" : " (" + project.getPath() + ")";
		runConfig.eclipseProjectName = project.getExtensions().getByType(EclipseModel.class).getProject().getName();

		runConfig.mainClass = "net.fabricmc.devlaunchinjector.Main";
		runConfig.vmArgs.add("-Dfabric.dli.config=" + encodeEscaped(extension.getFiles().getDevLauncherConfig().getAbsolutePath()));
		runConfig.vmArgs.add("-Dfabric.dli.env=" + environment.toLowerCase());
	}

	// Turns camelCase/PascalCase into Capital Case
	// caseConversionExample -> Case Conversion Example
	private static String capitalizeCamelCaseName(String name) {
		if (name.length() == 0) {
			return "";
		}

		return name.substring(0, 1).toUpperCase() + name.substring(1).replaceAll("([^A-Z])([A-Z])", "$1 $2");
	}

	public static RunConfig runConfig(Project project, RunConfigSettings settings) {
		LoomGradleExtension extension = LoomGradleExtension.get(project);
		String name = settings.getName();

		String configName = settings.getConfigName();
		String environment = settings.getEnvironment();
		SourceSet sourceSet = settings.getSource(project);

		String defaultMain = settings.getDefaultMainClass();

		if (defaultMain == null) {
			throw new IllegalArgumentException("Run configuration '" + name + "' must specify 'defaultMainClass'");
		}

		if (configName == null) {
			configName = "";
			String srcName = sourceSet.getName();

			if (!srcName.equals(SourceSet.MAIN_SOURCE_SET_NAME)) {
				configName += capitalizeCamelCaseName(srcName) + " ";
			}

			configName += "Minecraft " + capitalizeCamelCaseName(name);
		}

		Objects.requireNonNull(environment, "No environment set for run config");

		String runDir = settings.getRunDir();

		if (runDir == null) {
			runDir = "run";
		}

		RunConfig runConfig = new RunConfig();
		runConfig.configName = configName;
		populate(project, extension, runConfig, environment);
		runConfig.ideaModuleName = getIdeaModuleName(project, sourceSet);
		runConfig.runDirIdeaUrl = "file://$PROJECT_DIR$/" + runDir;
		runConfig.runDir = runDir;
		runConfig.sourceSet = sourceSet;

		// Custom parameters
		runConfig.programArgs.addAll(settings.getProgramArgs());
		runConfig.vmArgs.addAll(settings.getVmArgs());
		runConfig.vmArgs.add("-Dfabric.dli.main=" + getMainClass(environment, extension, defaultMain));

		return runConfig;
	}

	public String fromDummy(String dummy, boolean relativeDir, Project project) throws IOException {
		String dummyConfig;

		try (InputStream input = SetupIntelijRunConfigs.class.getClassLoader().getResourceAsStream(dummy)) {
			dummyConfig = new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}

		String runDir = this.runDir;

		if (relativeDir && project.getRootProject() != project) {
			Path rootPath = project.getRootDir().toPath();
			Path projectPath = project.getProjectDir().toPath();
			String relativePath = rootPath.relativize(projectPath).toString();

			runDir = relativePath + "/" + runDir;
		}

		dummyConfig = dummyConfig.replace("%NAME%", configName);
		dummyConfig = dummyConfig.replace("%MAIN_CLASS%", mainClass);
		dummyConfig = dummyConfig.replace("%ECLIPSE_PROJECT%", eclipseProjectName);
		dummyConfig = dummyConfig.replace("%IDEA_MODULE%", ideaModuleName);
		dummyConfig = dummyConfig.replace("%RUN_DIRECTORY%", runDir);
		dummyConfig = dummyConfig.replace("%PROGRAM_ARGS%", joinArguments(programArgs).replaceAll("\"", "&quot;"));
		dummyConfig = dummyConfig.replace("%VM_ARGS%", joinArguments(vmArgs).replaceAll("\"", "&quot;"));

		return dummyConfig;
	}

	public static String joinArguments(List<String> args) {
		final var sb = new StringBuilder();
		boolean first = true;

		for (String arg : args) {
			if (!first) {
				sb.append(" ");
			}

			first = false;
			sb.append("\"").append(arg).append("\"");
		}

		return sb.toString();
	}

	private static String getMainClass(String side, LoomGradleExtension extension, String defaultMainClass) {
		InstallerData installerData = extension.getInstallerData();

		if (installerData == null) {
			return defaultMainClass;
		}

		JsonObject installerJson = installerData.installerJson();

		if (installerJson != null && installerJson.has("mainClass")) {
			JsonElement mainClassJson = installerJson.get("mainClass");

			String mainClassName = "";

			if (mainClassJson.isJsonObject()) {
				JsonObject mainClassesJson = mainClassJson.getAsJsonObject();

				if (mainClassesJson.has(side)) {
					mainClassName = mainClassesJson.get(side).getAsString();
				}
			} else {
				mainClassName = mainClassJson.getAsString();
			}

			return mainClassName;
		}

		return defaultMainClass;
	}

	private static String encodeEscaped(String s) {
		StringBuilder ret = new StringBuilder();

		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);

			if (c == '@' && i > 0 && s.charAt(i - 1) == '@' || c == ' ') {
				ret.append(String.format("@@%04x", (int) c));
			} else {
				ret.append(c);
			}
		}

		return ret.toString();
	}
}
