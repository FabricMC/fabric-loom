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

package net.fabricmc.loom.test.unit

import java.nio.charset.StandardCharsets

import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.intellij.lang.annotations.Language
import spock.lang.Specification

import net.fabricmc.loom.configuration.ide.RunConfigurationInternal
import net.fabricmc.loom.configuration.ide.idea.IdeaSyncTask
import net.fabricmc.loom.test.util.GradleTestUtil
import net.fabricmc.loom.util.Arguments

class IdeaSyncTaskTest extends Specification {
	def "get run configs only includes generated root configs"() {
		given:
		def project = mockJavaProject("root")
		def generatedRun = mockRunConfig(project, "client", "Minecraft Client", true)
		def skippedRun = mockRunConfig(project, "server", "Minecraft Server", false)

		when:
		def configs = IdeaSyncTask.getRunConfigs(project, [generatedRun, skippedRun])

		then:
		configs.size() == 1
		configs[0].launchFile.get().asFile == new File(project.rootDir, ".idea/runConfigurations/Minecraft_Client.xml")
		configs[0].excludedLibraryPaths.get().empty
		configs[0].runConfigXml.get() == expectedRunConfigXml("Minecraft Client", "root.main", "\$PROJECT_DIR\$/run")
		project.file("run").directory
		!project.file("server-run").exists()
	}

	def "get run configs appends subproject path to launch file name"() {
		given:
		def rootProject = mockJavaProject("root")
		def project = mockJavaProject("sub", rootProject)
		def run = mockRunConfig(project, "client", "Minecraft Client", true)

		when:
		def configs = IdeaSyncTask.getRunConfigs(project, [run])

		then:
		configs.size() == 1
		configs[0].launchFile.get().asFile == new File(rootProject.rootDir, ".idea/runConfigurations/Minecraft_Client___sub__sub.xml")
		configs[0].runConfigXml.get() == expectedRunConfigXml("Minecraft Client (:sub)", "root.sub.main", "\$PROJECT_DIR\$/sub/run")
		project.file("run").directory
	}

	def "configure exclusions"() {
		when:
		def input = fromDummy()
		def output = IdeaSyncTask.setClasspathModificationsInXml(input, ["/path/to/file.jar"])

		then:
		output == EXPECTED
	}

	def "re-configure exclusions"() {
		when:
		def input = fromDummy()
		def output = IdeaSyncTask.setClasspathModificationsInXml(input, ["/path/to/file.jar"])
		output = IdeaSyncTask.setClasspathModificationsInXml(output, [
			"/path/to/file.jar",
			"/path/to/another.jar"
		])

		then:
		output == EXPECTED2
	}

	private String fromDummy() {
		String dummyConfig

		IdeaSyncTask.class.getClassLoader().getResourceAsStream("idea_run_config_template.xml").withCloseable {
			dummyConfig = new String(it.readAllBytes(), StandardCharsets.UTF_8)
		}

		dummyConfig = dummyConfig.replace("%NAME%", "Minecraft Client")
		dummyConfig = dummyConfig.replace("%MAIN_CLASS%", "net.minecraft.client.Main")
		dummyConfig = dummyConfig.replace("%IDEA_MODULE%", "main.test")
		dummyConfig = dummyConfig.replace("%RUN_DIRECTORY%", "\$PROJECT_DIR\$/.run")
		dummyConfig = dummyConfig.replace("%PROGRAM_ARGS%", Arguments.join([]).replaceAll("\"", "&quot;"))
		dummyConfig = dummyConfig.replace("%VM_ARGS%", Arguments.join([]).replaceAll("\"", "&quot;"))
		dummyConfig = dummyConfig.replace("%IDEA_FOLDER_NAME%", "")

		return dummyConfig
	}

	private static Project mockJavaProject(String name, Project parent = null) {
		def project = parent == null ? GradleTestUtil.mockProject(name) : GradleTestUtil.mockProject(name, parent)
		project.pluginManager.apply(JavaPlugin)
		return project
	}

	private static RunConfigurationInternal mockRunConfig(Project project, String name, String displayName, boolean generateRunConfig) {
		def run = project.objects.newInstance(RunConfigurationInternal.class, name)
		run.isFinalised.set(true)
		run.displayName.set(displayName)
		run.jvmArguments.set([])
		run.programArguments.set([])
		run.environmentVars.set([:])
		run.runtimeEnvironment.set("client")
		run.appendProjectPathToDisplayName.set(true)
		run.sourceSet.set("main")
		run.runDirectory.set(project.file(name == "server" ? "server-run" : "run"))
		run.generateRunConfig.set(generateRunConfig)
		run.ideConfigFolder.set((String) null)
		run.devLaunchMainClass.set("net.minecraft.client.Main")
		return run
	}

	private static String expectedRunConfigXml(String displayName, String moduleName, String runDirectory) {
		return """\
<component name="ProjectRunConfigurationManager">
  <configuration default="false" name="${displayName}" type="Application" factoryName="Application" >
    <option name="MAIN_CLASS_NAME" value="net.minecraft.client.Main" />
    <module name="${moduleName}" />
    <option name="PROGRAM_PARAMETERS" value="" />
    <option name="VM_PARAMETERS" value="" />
    <option name="WORKING_DIRECTORY" value="${runDirectory}/" />
    <method v="2">
      <option name="Make" enabled="true" />
    </method>
    <envs>
      
    </envs>
    <shortenClasspath name="ARGS_FILE" />
  </configuration>
</component>
"""
	}

	@Language("XML")
	private static final String EXPECTED = '''
<component name="ProjectRunConfigurationManager">
  <configuration default="false" factoryName="Application" name="Minecraft Client" type="Application">
    <option name="MAIN_CLASS_NAME" value="net.minecraft.client.Main"/>
    <module name="main.test"/>
    <option name="PROGRAM_PARAMETERS" value=""/>
    <option name="VM_PARAMETERS" value=""/>
    <option name="WORKING_DIRECTORY" value="$PROJECT_DIR$/.run/"/>
    <method v="2">
      <option enabled="true" name="Make"/>
    </method>
    <envs>
      %IDEA_ENV_VARS%
    </envs>
    <shortenClasspath name="ARGS_FILE"/>
  <classpathModifications><entry exclude="true" path="/path/to/file.jar"/></classpathModifications></configuration>
</component>
'''.trim()

	@Language("XML")
	private static final String EXPECTED2 = '''
<component name="ProjectRunConfigurationManager">
  <configuration default="false" factoryName="Application" name="Minecraft Client" type="Application">
    <option name="MAIN_CLASS_NAME" value="net.minecraft.client.Main"/>
    <module name="main.test"/>
    <option name="PROGRAM_PARAMETERS" value=""/>
    <option name="VM_PARAMETERS" value=""/>
    <option name="WORKING_DIRECTORY" value="$PROJECT_DIR$/.run/"/>
    <method v="2">
      <option enabled="true" name="Make"/>
    </method>
    <envs>
      %IDEA_ENV_VARS%
    </envs>
    <shortenClasspath name="ARGS_FILE"/>
  <classpathModifications><entry exclude="true" path="/path/to/file.jar"/><entry exclude="true" path="/path/to/another.jar"/></classpathModifications></configuration>
</component>
'''.trim()
}
