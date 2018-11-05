/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016, 2017, 2018 FabricMC
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

package net.fabricmc.loom.util;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.providers.MinecraftProvider;
import org.apache.commons.io.IOUtils;
import org.gradle.api.Project;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class IdeaRunConfig {
	public String configName;
	public String projectName;
	public String mainClass;
	public String runDir;
	public String vmArgs;
	public String programArgs;

	public Element genRuns(Element doc) throws IOException, ParserConfigurationException, TransformerException {
		Element root = this.addXml(doc, "component", ImmutableMap.of("name", "ProjectRunConfigurationManager"));
		root = addXml(root, "configuration", ImmutableMap.of("default", "false", "name", configName, "type", "Application", "factoryName", "Application"));

		this.addXml(root, "module", ImmutableMap.of("name", projectName));
		this.addXml(root, "option", ImmutableMap.of("name", "MAIN_CLASS_NAME", "value", mainClass));
		this.addXml(root, "option", ImmutableMap.of("name", "WORKING_DIRECTORY", "value", runDir));

		if (!Strings.isNullOrEmpty(vmArgs)) {
			this.addXml(root, "option", ImmutableMap.of("name", "VM_PARAMETERS", "value", vmArgs));
		}

		if (!Strings.isNullOrEmpty(programArgs)) {
			this.addXml(root, "option", ImmutableMap.of("name", "PROGRAM_PARAMETERS", "value", programArgs));
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

	public static IdeaRunConfig clientRunConfig(Project project){
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		MinecraftProvider minecraftProvider =  extension.getDependencyManager().getProvider(MinecraftProvider.class);
		MinecraftVersionInfo minecraftVersionInfo = minecraftProvider.versionInfo;

		IdeaRunConfig ideaClient = new IdeaRunConfig();
		ideaClient.mainClass = "net.minecraft.launchwrapper.Launch";
		ideaClient.projectName = project.getName();
		ideaClient.configName = "Minecraft Client";
		ideaClient.runDir = "file://$PROJECT_DIR$/" + extension.runDir;
		ideaClient.vmArgs = "-Dfabric.development=true";
		ideaClient.programArgs = "--tweakClass " + Constants.FABRIC_CLIENT_TWEAKER + " --assetIndex " + minecraftVersionInfo.assetIndex.getFabricId(extension.getMinecraftProvider().minecraftVersion) + " --assetsDir \"" + new File(extension.getUserCache(), "assets").getAbsolutePath() + "\" --fabricMappingFile \"" + minecraftProvider.pomfProvider.MAPPINGS_TINY.getAbsolutePath() + "\"";

		return ideaClient;
	}

	public static IdeaRunConfig serverRunConfig(Project project){
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		MinecraftProvider minecraftProvider =  extension.getDependencyManager().getProvider(MinecraftProvider.class);
		MinecraftVersionInfo minecraftVersionInfo = minecraftProvider.versionInfo;

		IdeaRunConfig ideaServer = new IdeaRunConfig();
		ideaServer.mainClass = "net.minecraft.launchwrapper.Launch";
		ideaServer.projectName = project.getName();
		ideaServer.configName = "Minecraft Server";
		ideaServer.runDir = "file://$PROJECT_DIR$/" + extension.runDir;
		ideaServer.vmArgs = "-Dfabric.development=true";
		ideaServer.programArgs = "--tweakClass " + Constants.FABRIC_SERVER_TWEAKER + " --fabricMappingFile \"" + minecraftProvider.pomfProvider.MAPPINGS_TINY.getAbsolutePath() + "\"";

		return ideaServer;
	}

	public String fromDummy() throws IOException {
		InputStream input = SetupIntelijRunConfigs.class.getClassLoader().getResourceAsStream("idea_run_config_template.xml");
		String dummyConfig = IOUtils.toString(input, StandardCharsets.UTF_8);
		input.close();

		dummyConfig = dummyConfig.replace("%NAME%", configName);
		dummyConfig = dummyConfig.replace("%MAIN_CLASS%", mainClass);
		dummyConfig = dummyConfig.replace("%MODULE%", projectName);
		dummyConfig = dummyConfig.replace("%PROGRAM_ARGS%", programArgs.replaceAll("\"", "&quot;"));
		dummyConfig = dummyConfig.replace("%VM_ARGS%", vmArgs.replaceAll("\"", "&quot;"));

		return dummyConfig;
	}
}