package net.fabricmc.loom.ide.idea.resolving;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.SourceSet;
import org.gradle.util.GUtil;
import org.jetbrains.plugins.gradle.model.ExternalDependency;
import org.jetbrains.plugins.gradle.tooling.util.resolve.CompileDependenciesProvider;
import org.jetbrains.plugins.gradle.tooling.util.resolve.DependencyResolverImpl;
import org.jetbrains.plugins.gradle.tooling.util.resolve.ExternalDepsResolutionResult;

public class IdeaLoomCompileDependencyProvider {
    public static final String SCOPE = "COMPILE";

    private final SourceSet                      mySourceSet;
    private final Project                        myProject;
    private       Configuration                  myConfiguration;
    private       Configuration                  myCompileClasspathConfiguration;
    private       Configuration                  myCompileConfiguration;
    private       Configuration                  myCompileOnlyConfiguration;
    private       Collection<ExternalDependency> myDependencies;
    private       Collection<File>               myFiles;
    private final Map<Configuration, Set<File>>  myConfigurationFilesCache = new HashMap<Configuration, Set<File>>();

    public IdeaLoomCompileDependencyProvider(SourceSet sourceSet, Project project) {
        mySourceSet = sourceSet;
        myProject = project;
    }

    public Set<File> getDeprecatedCompileConfigurationFiles() {
        return getFilesFromCache(myConfiguration);
    }

    public Configuration getCompileClasspathConfiguration() {
        return myCompileClasspathConfiguration;
    }

    public Set<File> getCompileConfigurationFiles() {
        return getFilesFromCache(myCompileConfiguration);
    }

    public Set<File> getCompileOnlyConfigurationFiles() {
        return getFilesFromCache(myCompileOnlyConfiguration);
    }

    private Set<File> getFilesFromCache(Configuration key) {
        if (key == null) {
            return null;
        }
        Set<File> cached = myConfigurationFilesCache.get(key);
        if (cached == null) {
            cached = key.getResolvedConfiguration().getLenientConfiguration().getFiles(Specs.SATISFIES_ALL);
            myConfigurationFilesCache.put(key, cached);
        }
        return cached;
    }

    public Collection<ExternalDependency> getDependencies() {
        return myDependencies;
    }

    public Collection<File> getFiles() {
        return myFiles;
    }

    public IdeaLoomCompileDependencyProvider resolve(IdeaLoomDependencyResolver resolver) {
        // resolve compile dependencies
        boolean isMainSourceSet = mySourceSet.getName().equals(SourceSet.MAIN_SOURCE_SET_NAME);
        String deprecatedCompileConfigurationName = isMainSourceSet ? "compile" : GUtil.toCamelCase(mySourceSet.getName()) + "Compile";
        String compileConfigurationName = mySourceSet.getCompileConfigurationName();

        myConfiguration = myProject.getConfigurations().findByName(deprecatedCompileConfigurationName);
        myCompileClasspathConfiguration = myProject.getConfigurations().findByName(compileConfigurationName + "Classpath");
        myCompileConfiguration = myProject.getConfigurations().findByName(compileConfigurationName);
        Configuration compileConfiguration = myCompileClasspathConfiguration != null ? myCompileClasspathConfiguration
                                                             : myCompileConfiguration;
        myCompileOnlyConfiguration =
                        IdeaLoomDependencyResolver.isJavaLibraryPluginSupported
                                        ? myProject.getConfigurations().findByName(mySourceSet.getCompileOnlyConfigurationName()) : null;

        ExternalDepsResolutionResult externalDepsResolutionResult = resolver.resolveDependencies(compileConfiguration, SCOPE);
        myDependencies = externalDepsResolutionResult.getExternalDeps();
        myFiles = externalDepsResolutionResult.getResolvedFiles();
        return this;
    }
}
