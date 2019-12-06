package net.fabricmc.loom.ide;

import java.lang.reflect.Field;
import java.util.List;

import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.initialization.DefaultGradleLauncher;
import org.gradle.initialization.GradleLauncher;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.NestedBuildState;
import org.gradle.internal.build.RootBuildState;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.BuildScopeServices;
import org.gradle.plugins.ide.internal.tooling.GradleProjectBuilder;
import org.gradle.plugins.ide.internal.tooling.IdeaModelBuilder;
import org.gradle.tooling.provider.model.ToolingModelBuilder;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

import net.fabricmc.loom.ide.idea.CustomDependencyResolvingIdeaModelBuilder;

public final class ToolingModelBuilderOverrideHandler {

    private ToolingModelBuilderOverrideHandler() {
        throw new IllegalStateException("Tried to initialize: ToolingModelBuilderOverrideHandler but this is a Utility class.");
    }

    /**
     * This method applies the custom tooling model builder that is need to resolve dependencies properly
     * @param project The project to apply the custom model tooling builder to.
     */
    public static void apply(final Project project)
    {
        final BuildScopeServices buildScopeServices = getBuildScopeServicesFromProject(project);
        if (buildScopeServices == null)
        {
            project.getLogger().error("Failed to get buildscope services! Resolution of Sources and Javadoc Artifacts might not work in this setup!");
        }

        injectIntoToolingRegistry(buildScopeServices);
        injectIntoToolingRegistry(((ProjectInternal) project).getServices());
    }

    private static void injectIntoToolingRegistry(final ServiceRegistry serviceRegistry)
    {
        final ToolingModelBuilderRegistry registry = (ToolingModelBuilderRegistry) serviceRegistry.find(ToolingModelBuilderRegistry.class);
        try {
            final Field field = registry.getClass().getDeclaredField("builders");
            field.setAccessible(true);
            final List<ToolingModelBuilder> builders = (List<ToolingModelBuilder>) field.get(registry);

            final GradleProjectBuilder gradleProjectBuilder =
                            (GradleProjectBuilder) builders.stream().filter(builder -> builder instanceof GradleProjectBuilder).findFirst().orElse(new GradleProjectBuilder());
            builders.removeIf(builder -> builder instanceof IdeaModelBuilder);
            builders.add(new CustomDependencyResolvingIdeaModelBuilder(gradleProjectBuilder, serviceRegistry));
        }
        catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    private static BuildScopeServices getBuildScopeServicesFromProject(final Project project)
    {
        final ProjectInternal projectInternal = (ProjectInternal) project;
        final ProjectState projectState = projectInternal.getMutationState();
        final BuildState buildState = projectState.getOwner();
        //We can only deal with root build states.
        if (!(buildState instanceof RootBuildState) || (buildState instanceof NestedBuildState))
        {
            return null;
        }

        final GradleLauncher gradleLauncher = getFieldValue(buildState, "gradleLauncher");
        if(!(gradleLauncher instanceof DefaultGradleLauncher))
        {
            return null;
        }
        final DefaultGradleLauncher launcher = (DefaultGradleLauncher) gradleLauncher;
        final BuildScopeServices buildScopeServices = getFieldValue(launcher, "buildServices");
        return buildScopeServices;
    }

    private static <T> T getFieldValue(final Object object, final String fieldName)
    {
        try {
            final Field field = object.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return (T) field.get(object);
        }
        catch (IllegalAccessException | NoSuchFieldException e) {
            e.printStackTrace();
            return null;
        }
    }
}
