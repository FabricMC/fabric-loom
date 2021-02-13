package net.fabricmc.loom

import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Ignore
import spock.lang.Specification
import spock.lang.Unroll

import static net.fabricmc.loom.BuildUtils.*
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

/**
 * Created by Mitchell Skaggs on 6/10/2019.
 */
class MixinBuildFunctionalTest extends Specification {
	@Rule
	TemporaryFolder testProjectDir = new TemporaryFolder()
	File settingsFile
	File buildFile
	File propsFile
	File modJsonFile
	File modJavaFile
	File modMixinsJsonFile
	File modMixinsJavaFile

	def setup() {
		settingsFile = testProjectDir.newFile('settings.gradle')
		buildFile = testProjectDir.newFile('build.gradle')
		propsFile = testProjectDir.newFile('gradle.properties')

		testProjectDir.newFolder("src", "main", "resources")
		modJsonFile = testProjectDir.newFile('src/main/resources/fabric.mod.json')
		modMixinsJsonFile = testProjectDir.newFile('src/main/resources/modid.mixins.json')

		testProjectDir.newFolder("src", "main", "java", "net", "fabricmc", "example")
		modJavaFile = testProjectDir.newFile("src/main/java/net/fabricmc/example/ExampleMod.java")

		testProjectDir.newFolder("src", "main", "java", "net", "fabricmc", "example", "mixin")
		modMixinsJavaFile = testProjectDir.newFile("src/main/java/net/fabricmc/example/mixin/ExampleMixin.java")
	}

	@Unroll
	def "mixin build succeeds using Minecraft #mcVersion"() {
		given:
		settingsFile << genSettingsFile("mixin-build-functional-test")
		propsFile << genPropsFile(mcVersion, yarnVersion, loaderVersion, fabricVersion)
		buildFile << genBuildFile()
		modJsonFile << genModJsonFile()
		modJavaFile << genModJavaFile()
		modMixinsJsonFile << genModMixinsJsonFile()
		modMixinsJavaFile << genModMixinsJavaFile()

		when:
		def result = GradleRunner.create()
				.withProjectDir(testProjectDir.root)
				.withArguments('build','--stacktrace')
				.withArguments("--warning-mode", System.getenv().TEST_WARNING_MODE ?: 'all')
				.withPluginClasspath()
				.forwardOutput()
				.build()

		then:
		result.task(":build").outcome == SUCCESS

		where:
		mcVersion | yarnVersion       	| loaderVersion     | fabricVersion
		'1.14'    | '1.14+build.21'   	| '0.4.8+build.155' | '0.3.0+build.184'
	}
}
