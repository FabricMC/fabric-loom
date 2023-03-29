/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022 FabricMC
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

import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import org.intellij.lang.annotations.Language
import spock.lang.Shared
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

import net.fabricmc.loom.util.gradle.SourceSetHelper
import net.fabricmc.loom.util.gradle.SourceSetReference

class SourceSetHelperTest extends Specification {
	@Shared
	private static File projectDir = File.createTempDir()

	@RestoreSystemProperties
	def "idea classpath"() {
		given:
		def miscXml = new File(projectDir, ".idea/misc.xml")
		miscXml.parentFile.mkdirs()
		miscXml.text = MISC_XML

		def mockProject = Mock(Project)
		def mockSourceSet = Mock(SourceSet)

		mockProject.getName() >> "UnitTest"
		mockProject.getRootDir() >> projectDir
		mockSourceSet.getName() >> "main"

		System.setProperty("fabric-loom.unit.testing", "true")

		def ref = new SourceSetReference(mockSourceSet, mockProject)
		when:
		def result = SourceSetHelper.getIdeaClasspath(ref, mockProject)

		then:
		result.size() == 1
		!result[0].toString().startsWith("file:")

		println(result[0].toString())
	}

	@RestoreSystemProperties
	def "eclipse classpath"() {
		given:
		def classpath = new File(projectDir, ".classpath")
		classpath.createNewFile()

		def binDir = new File(projectDir, "bin")
		binDir.mkdirs()

		def mockProject = Mock(Project)
		def mockSourceSet = Mock(SourceSet)

		mockProject.getName() >> "UnitTest"
		mockProject.getProjectDir() >> projectDir
		mockSourceSet.getName() >> "main"

		System.setProperty("fabric-loom.unit.testing", "true")

		def ref = new SourceSetReference(mockSourceSet, mockProject)
		when:
		def result = SourceSetHelper.getEclipseClasspath(ref, mockProject)

		then:
		result.size() == 1
		println(result[0].toString())
	}

	@RestoreSystemProperties
	def "vscode classpath"() {
		given:
		def dotVscode = new File(projectDir, ".vscode")
		dotVscode.mkdirs()

		def binDir = new File(projectDir, "bin")
		binDir.mkdirs()

		def mockProject = Mock(Project)
		def mockSourceSet = Mock(SourceSet)

		mockProject.getName() >> "UnitTest"
		mockProject.getProjectDir() >> projectDir
		mockSourceSet.getName() >> "main"

		System.setProperty("fabric-loom.unit.testing", "true")

		def ref = new SourceSetReference(mockSourceSet, mockProject)
		when:
		def result = SourceSetHelper.getVscodeClasspath(ref, mockProject)

		then:
		result.size() == 1
		println(result[0].toString())
	}

	@Language("xml")
	private static String MISC_XML = """<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="ExternalStorageConfigurationManager" enabled="true" />
  <component name="ProjectRootManager" version="2" languageLevel="JDK_17" default="true" project-jdk-name="17" project-jdk-type="JavaSDK">
    <output url="file://\$PROJECT_DIR\$/build/out" />
  </component>
</project>"""
}
