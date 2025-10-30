package net.fabricmc.loom.test.integration

import spock.lang.Specification
import spock.lang.Unroll

import net.fabricmc.loom.test.util.GradleProjectTestTrait

import static net.fabricmc.loom.test.LoomTestConstants.PRE_RELEASE_GRADLE
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class NotObfuscatedTest extends Specification implements GradleProjectTestTrait {
	@Unroll
	def "Not Obfuscated"() {
		setup:
		def gradle = gradleProject(project: "minimalBase", version: PRE_RELEASE_GRADLE)
		gradle.buildGradle << '''
				dependencies {
					minecraft 'com.mojang:minecraft:1.21.10'
                    api "net.fabricmc.fabric-api:fabric-api:0.134.1+1.21.10"
                }
		'''
		gradle.getGradleProperties() << "fabric.loom.disableObfuscation=true"

		when:
		def result = gradle.run(task: "build")

		then:
		result.task(":build").outcome == SUCCESS
	}
}
