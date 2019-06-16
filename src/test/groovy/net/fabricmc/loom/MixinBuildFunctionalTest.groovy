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

	/**
	 * Why it's ignored:
	 * <p>
	 * When doing mixin annotation processing, a {@link ServiceLoader} is used to find implementations of {@link org.spongepowered.tools.obfuscation.service.IObfuscationService}. The classpath isn't passed to the build properly in this case, causing the Fabric-specific {@link net.fabricmc.loom.mixin.ObfuscationServiceFabric} to not be loaded. This causes the mixin to fail to compile because it doesn't know how to be reobfuscated.
	 */
	@Unroll
	@Ignore("fails because the annotation processor classpath doesn't include fabric-mixin-compile-extensions, so it doesn't know how to remap anything")
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
				.withArguments('build')
				.withPluginClasspath()
				.withGradleVersion("5.1.1")
				.build()

		then:
		result.task(":build").outcome == SUCCESS

		where:
		mcVersion | yarnVersion       | loaderVersion     | fabricVersion
		'1.14'    | '1.14+build.21'   | '0.4.8+build.155' | '0.3.0+build.184'
		'1.14.1'  | '1.14.1+build.10' | '0.4.8+build.155' | '0.3.0+build.184'
		'1.14.2'  | '1.14.2+build.7'  | '0.4.8+build.155' | '0.3.0+build.184'
	}
}
