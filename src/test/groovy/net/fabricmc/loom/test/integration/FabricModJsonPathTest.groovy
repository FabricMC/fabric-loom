package net.fabricmc.loom.test.integration

import net.fabricmc.loom.test.util.GradleProjectTestTrait
import org.gradle.testkit.runner.BuildResult
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class FabricModJsonPathTest extends Specification implements GradleProjectTestTrait {
    @Unroll
    def "Resolve custom FMJ"() {
        setup:
        GradleProject gradle = gradleProject(project: "fmjPathConfig")

        when:
        BuildResult result = gradle.run(task: "build", args: ["-PoverrideFMJ=true"])

        then:
        result.task(":build").outcome == SUCCESS
    }

    @Unroll
    def "Fail to find FMJ"() {
        setup:
        GradleProject gradle = gradleProject(project: "fmjPathConfig")

        when:
        BuildResult result = gradle.run(task: "build", expectFailure: true)

        then:
        result.task(":build") == null
    }
}
