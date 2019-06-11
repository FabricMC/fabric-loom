package net.fabricmc.loom

import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

/**
 * Created by Mitchell Skaggs on 6/10/2019.
 */
class EmptyBuildFunctionalTest extends Specification {
	@Rule
	TemporaryFolder testProjectDir = new TemporaryFolder()
	File settingsFile
	File buildFile
	File propsFile

	def setup() {
		settingsFile = testProjectDir.newFile('settings.gradle')
		buildFile = testProjectDir.newFile('build.gradle')
		propsFile = testProjectDir.newFile('gradle.properties')
	}

	@Unroll
	def "empty build succeeds using Minecraft #mcVersion"() {
		given:
		settingsFile << """
rootProject.name = 'empty-build-functional-test'
"""

		propsFile << """
org.gradle.caching=true
org.gradle.parallel=true

# Fabric Properties
# check these on https://fabricmc.net/use
minecraft_version=$mcVersion
yarn_mappings=$yarnVersion
loader_version=$loaderVersion

# Mod Properties
mod_version = 1.0.0
maven_group = net.fabricmc
archives_base_name = fabric-example-mod

# Dependencies
# currently not on the main fabric site, check on the maven: https://maven.fabricmc.net/net/fabricmc/fabric
fabric_version=$fabricVersion
"""

		buildFile << """
plugins {
	id 'fabric-loom'
	id 'maven-publish'
}
sourceCompatibility = JavaVersion.VERSION_1_8
targetCompatibility = JavaVersion.VERSION_1_8

archivesBaseName = project.archives_base_name
version = project.mod_version
group = project.maven_group

minecraft {
}

dependencies {
	//to change the versions see the gradle.properties file
	minecraft "com.mojang:minecraft:\${project.minecraft_version}"
	mappings "net.fabricmc:yarn:\${project.yarn_mappings}"
	modCompile "net.fabricmc:fabric-loader:\${project.loader_version}"

	// Fabric API. This is technically optional, but you probably want it anyway.
	modCompile "net.fabricmc.fabric-api:fabric-api:\${project.fabric_version}"

	// PSA: Some older mods, compiled on Loom 0.2.1, might have outdated Maven POMs.
	// You may need to force-disable transitiveness on them.
}

processResources {
	inputs.property "version", project.version

	from(sourceSets.main.resources.srcDirs) {
		include "fabric.mod.json"
		expand "version": project.version
	}

	from(sourceSets.main.resources.srcDirs) {
		exclude "fabric.mod.json"
	}
}

// ensure that the encoding is set to UTF-8, no matter what the system default is
// this fixes some edge cases with special characters not displaying correctly
// see http://yodaconditions.net/blog/fix-for-java-file-encoding-problems-with-gradle.html
tasks.withType(JavaCompile) {
	options.encoding = "UTF-8"
}

// Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
// if it is present.
// If you remove this task, sources will not be generated.
task sourcesJar(type: Jar, dependsOn: classes) {
	classifier = "sources"
	from sourceSets.main.allSource
}

jar {
	from "LICENSE"
}

// configure the maven publication
publishing {
	publications {
		mavenJava(MavenPublication) {
			// add all the jars that should be included when publishing to maven
			artifact(jar) {
				builtBy remapJar
			}
			artifact(sourcesJar) {
				builtBy remapSourcesJar
			}
		}
	}

	// select the repositories you want to publish to
	repositories {
		// uncomment to publish to the local maven
		// mavenLocal()
	}
}
"""

		when:
		def result = GradleRunner.create()
				.withProjectDir(testProjectDir.root)
				.withArguments('build')
				.withPluginClasspath()
				.build()

		then:
		//result.output.contains('Hello world!')
		result.task(":build").outcome == SUCCESS

		where:
		mcVersion | yarnVersion       | loaderVersion     | fabricVersion
		'1.14'    | '1.14+build.21'   | '0.4.8+build.155' | '0.3.0+build.183'
		'1.14.1'  | '1.14.1+build.10' | '0.4.8+build.155' | '0.3.0+build.183'
		'1.14.2'  | '1.14.2+build.7'  | '0.4.8+build.155' | '0.3.0+build.183'
	}
}
