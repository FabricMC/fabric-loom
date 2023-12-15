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

import org.intellij.lang.annotations.Language
import spock.lang.Specification

import net.fabricmc.loom.configuration.ide.RunConfig
import net.fabricmc.loom.configuration.ide.idea.IdeaSyncTask

class IdeaClasspathModificationsTest extends Specification {

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
		dummyConfig = dummyConfig.replace("%RUN_DIRECTORY%", ".run")
		dummyConfig = dummyConfig.replace("%PROGRAM_ARGS%", RunConfig.joinArguments([]).replaceAll("\"", "&quot;"))
		dummyConfig = dummyConfig.replace("%VM_ARGS%", RunConfig.joinArguments([]).replaceAll("\"", "&quot;"))

		return dummyConfig
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
