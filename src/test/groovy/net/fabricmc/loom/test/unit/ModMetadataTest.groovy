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

package net.fabricmc.loom.test.unit

import java.nio.file.Path

import org.gradle.api.attributes.Attribute
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

import net.fabricmc.loom.LoomGradleExtension
import net.fabricmc.loom.configuration.processors.speccontext.ModMetadata
import net.fabricmc.loom.configuration.processors.speccontext.ModMetadataResolver
import net.fabricmc.loom.util.gradle.SourceSetHelper

class ModMetadataTest extends Specification {
	@TempDir
	Path projectDir

	def "variant lazily publishes only referenced files using main and client resource precedence"() {
		given:
		def project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build()
		project.pluginManager.apply("java")
		def sourceSets = SourceSetHelper.getSourceSets(project)
		def main = sourceSets.getByName("main")
		def client = sourceSets.create("client")
		project.extensions.add("loom", Stub(LoomGradleExtension) {
			getMinecraftSourceSet() >> main
			getMinecraftClientSourceSet() >> client
			areEnvironmentSourceSetsSplit() >> true
			getFabricModJsonPath() >> project.objects.fileProperty()
		})
		ModMetadata.register(project)
		main.resources.setSrcDirs(["metadata", "extra-resources"])
		client.resources.setSrcDirs(["client-resources"])
		writeFile("metadata/fabric.mod.json", '''{
	"schemaVersion": 2,
	"id": "example",
	"version": "1.0.0",
	"classTweakers": ["common/tweaks.classtweaker", {"config": "client/tweaks.classtweaker", "environment": "client"}],
	"custom": {"loom:provided_javadoc": "docs.tiny"}
}''')
		writeFile("metadata/common/tweaks.classtweaker", "main")
		writeFile("extra-resources/common/tweaks.classtweaker", "extra")
		writeFile("extra-resources/docs.tiny", "docs")
		writeFile("client-resources/client/tweaks.classtweaker", "client")
		writeFile("extra-resources/unrelated.txt", "unrelated")

		when:
		def configuration = project.configurations.getByName("loomSourceModMetadata")
		def artifacts = configuration.outgoing.artifacts.toList()

		then:
		artifacts*.file.toSet() == [
			project.file("metadata/fabric.mod.json"),
			project.file("metadata/common/tweaks.classtweaker"),
			project.file("client-resources/client/tweaks.classtweaker"),
			project.file("extra-resources/docs.tiny")
		].toSet()
	}

	def "metadata override resolves referenced files relative to its directory"() {
		given:
		def project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build()
		def overridePath = project.objects.fileProperty().fileValue(project.file("overrides/selected.json"))
		project.extensions.add("loom", Stub(LoomGradleExtension) {
			getFabricModJsonPath() >> overridePath
		})
		ModMetadata.register(project)
		writeFile("overrides/selected.json", '''{
	"schemaVersion": 1,
	"id": "example",
	"version": "1.0.0",
	"accessWidener": "nested/example.accesswidener"
}''')
		writeFile("overrides/nested/example.accesswidener", "override")
		writeFile("src/main/resources/nested/example.accesswidener", "main")

		when:
		def artifacts = project.configurations.getByName("loomSourceModMetadata").outgoing.artifacts.toList()

		then:
		artifacts*.file == [
			project.file("overrides/selected.json"),
			project.file("overrides/nested/example.accesswidener")
		]
	}

	def "project without source metadata publishes no artifacts"() {
		given:
		def project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build()
		project.pluginManager.apply("java")
		project.extensions.add("loom", Stub(LoomGradleExtension) {
			getMinecraftSourceSet() >> SourceSetHelper.getMainSourceSet(project)
			getFabricModJsonPath() >> project.objects.fileProperty()
		})
		ModMetadata.register(project)

		expect:
		project.configurations.getByName("loomSourceModMetadata").outgoing.artifacts.empty
	}

	def "metadata resolution accepts dependencies with #selection"() {
		given:
		def project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build()
		def configuration = project.configurations.create("dependencies")
		def dependency = project.dependencies.create("example:producer:1.0.0")

		switch (selection) {
			case "attributes":
				dependency.attributes {
					attribute(Attribute.of("example.variant", String), "extra")
				}
				break
			case "capabilities":
				dependency.capabilities {
					requireCapability("example:producer-extra")
				}
				break
			case "target configuration":
				dependency.targetConfiguration = "namedElements"
				break
		}

		configuration.dependencies.add(dependency)
		def resolver = new ModMetadataResolver(project)

		expect:
		resolver.resolve(configuration).empty
		dependency.targetConfiguration == (selection == "target configuration" ? "namedElements" : null)

		where:
		selection << [
			"attributes",
			"capabilities",
			"target configuration"
		]
	}

	private void writeFile(String path, String contents) {
		def file = projectDir.resolve(path).toFile()
		file.parentFile.mkdirs()
		file.setText(contents, "UTF-8")
	}
}
