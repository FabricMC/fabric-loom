package net.fabricmc.loom.test.integration

import net.fabricmc.loom.test.util.GradleProjectTestTrait
import spock.lang.Specification
import spock.lang.Unroll

import static net.fabricmc.loom.test.LoomTestConstants.STANDARD_TEST_VERSIONS
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class IncludedJarsTest extends Specification implements GradleProjectTestTrait {
    @Unroll
    def "included jars (gradle #version)"() {
        setup:
        def gradle = gradleProject(project: "includedJars", version: version)

        when:
        def result = gradle.run(tasks: ["remapJar"])

        then:
        result.task(":remapJar").outcome == SUCCESS

        // Assert directly declared dependencies are present
        gradle.hasOutputZipEntry("includedJars.jar", "META-INF/jars/log4j-core-2.22.0.jar")
        gradle.hasOutputZipEntry("includedJars.jar", "META-INF/jars/adventure-text-serializer-gson-4.14.0.jar")

        // But not transitives.
        !gradle.hasOutputZipEntry("includedJars.jar", "META-INF/jars/log4j-api-2.22.0.jar")
        !gradle.hasOutputZipEntry("includedJars.jar", "META-INF/jars/adventure-api-4.14.0.jar")

        where:
        version << STANDARD_TEST_VERSIONS
    }
}
