package net.fabricmc.loom.ide;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.initialization.Settings;
import org.gradle.api.invocation.Gradle;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;
import org.gradle.util.GradleVersion;

public class LoomIdePlugin implements Plugin<Gradle> {

    @Override
    public void apply(final Gradle gradle) {
        //final Gradle gradle = settings.getGradle();

        final ToolingModelBuilderRegistryProcessor processor = new ToolingModelBuilderRegistryProcessor();
        gradle.addProjectEvaluationListener(processor);
        final boolean projectEvaluationIsNotCalledForIncludedBuilds = GradleVersion.current().compareTo(GradleVersion.version("3.1")) >= 0  &&
                                                                            GradleVersion.current().compareTo(GradleVersion.version("4.0")) < 0 ;
        if (projectEvaluationIsNotCalledForIncludedBuilds) {
            gradle.getRootProject().afterEvaluate(project -> {
                gradle.getIncludedBuilds().forEach(build -> {
                    final ToolingModelBuilderRegistry registry = ((IncludedBuildState) build).getConfiguredBuild().getServices().get(ToolingModelBuilderRegistry.class);
                    processor.process(registry, ((IncludedBuildState) build).getConfiguredBuild().getServices());
                });
            });
        }
    }
}
