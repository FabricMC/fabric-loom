package net.fabricmc.loom

import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification
import spock.lang.Unroll

import static net.fabricmc.loom.BuildUtils.*
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

/**
 * Created by Mitchell Skaggs on 6/10/2019.
 */
class EmptyBuildMojangFunctionalTest extends Specification {
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
		settingsFile << genSettingsFile("empty-build-functional-test")
		propsFile << genPropsFile(mcVersion, "nope", loaderVersion, fabricVersion)
		buildFile << genBuildFile("minecraft.officialMojangMappings()")

		when:
		def result = GradleRunner.create()
				.withProjectDir(testProjectDir.root)
				.withArguments('build',"--stacktrace", "--warning-mode", System.getenv().TEST_WARNING_MODE ?: 'all')
				.withPluginClasspath()
				.forwardOutput()
				.build()

		then:
		result.task(":build").outcome == SUCCESS

		where:
		mcVersion	| loaderVersion     | fabricVersion
		'1.16.2'	| '0.9.2+build.206' | '0.19.0+build.398-1.16'
	}
}
