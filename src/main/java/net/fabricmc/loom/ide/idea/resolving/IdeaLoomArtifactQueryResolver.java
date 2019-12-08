package net.fabricmc.loom.ide.idea.resolving;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.LenientConfiguration;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.artifacts.UnresolvedDependency;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.result.ComponentArtifactsResult;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.component.Artifact;
import org.gradle.api.component.Component;
import org.gradle.api.specs.Specs;
import org.gradle.internal.impldep.com.google.common.collect.ArrayListMultimap;
import org.gradle.internal.impldep.com.google.common.collect.Multimap;
import org.gradle.internal.impldep.com.google.common.collect.Sets;
import org.gradle.language.base.artifact.SourcesArtifact;
import org.gradle.language.java.artifact.JavadocArtifact;
import org.jetbrains.plugins.gradle.model.DefaultFileCollectionDependency;
import org.jetbrains.plugins.gradle.model.ExternalDependency;
import org.jetbrains.plugins.gradle.tooling.util.SourceSetCachedFinder;
import org.jetbrains.plugins.gradle.tooling.util.resolve.DependencyResolverImpl;
import org.jetbrains.plugins.gradle.tooling.util.resolve.ExternalDepsResolutionResult;

public class IdeaLoomArtifactQueryResolver {
    private final Configuration         myConfiguration;
    private final String                myScope;
    private final Project               myProject;
    private final boolean               myDownloadSources;
    private final boolean               myDownloadJavadoc;
    private final IdeaLoomSourceSetCachedFinder mySourceSetFinder;

    IdeaLoomArtifactQueryResolver(
                    final Configuration configuration,
                    final String scope,
                    final Project project,
                    final boolean downloadJavadoc,
                    final boolean downloadSources,
                    final IdeaLoomSourceSetCachedFinder sourceSetFinder) {
        myConfiguration = configuration;
        myScope = scope;
        myProject = project;
        myDownloadJavadoc = downloadJavadoc;
        myDownloadSources = downloadSources;
        mySourceSetFinder = sourceSetFinder;
    }

    public ExternalDepsResolutionResult resolve() {
        final Collection<ExternalDependency> extDependencies = new LinkedHashSet<ExternalDependency>();

        Class<? extends Component> jvmLibrary = tryLoadingJvmLibraryClass();

        if (jvmLibrary == null) {
            return ExternalDepsResolutionResult.EMPTY;
        }

        LenientConfiguration lenientConfiguration = myConfiguration.getResolvedConfiguration().getLenientConfiguration();
        Set<UnresolvedDependency> unresolvedModuleDependencies = lenientConfiguration.getUnresolvedModuleDependencies();
        Set<ResolvedArtifact> resolvedArtifacts;
        if (unresolvedModuleDependencies.isEmpty() || !IdeaLoomDependencyResolver.is31OrBetter) {
            resolvedArtifacts = lenientConfiguration.getArtifacts(Specs.SATISFIES_ALL);
        }
        else {
            resolvedArtifacts = new LinkedHashSet<ResolvedArtifact>();
            // org.gradle.api.artifacts.LenientConfiguration.getAllModuleDependencies method was added in Gradle 3.1
            Set<ResolvedDependency> allModuleDependencies = lenientConfiguration.getAllModuleDependencies();
            for (ResolvedDependency dependency : allModuleDependencies) {
                try {
                    resolvedArtifacts.addAll(dependency.getModuleArtifacts());
                }
                catch (Exception ignore) {
                    // ignore org.gradle.internal.resolve.ArtifactResolveException
                }
            }
        }

        final Multimap<ModuleVersionIdentifier, ResolvedArtifact> artifactMap =
                        groupByModuleVersionId(resolvedArtifacts);
        final Map<ComponentIdentifier, ComponentArtifactsResult> auxiliaryArtifactsMap =
                        buildAuxiliaryArtifactsMap(jvmLibrary, resolvedArtifacts);

        final Multimap<ModuleComponentIdentifier, ProjectDependency> configurationProjectDependencies =
                        IdeaLoomDependencyResolver.collectProjectDeps(myConfiguration);

        if (!myConfiguration.getResolvedConfiguration().hasError()) {
            extDependencies.addAll(buildFileCollectionDeps(resolvedArtifacts, configurationProjectDependencies.values()));
        }

        IdeaLoomDependencyResultsTransformer dependencyResultsTransformer =
                        new IdeaLoomDependencyResultsTransformer(myProject, mySourceSetFinder,
                                        artifactMap,
                                        auxiliaryArtifactsMap,
                                        configurationProjectDependencies,
                                        myScope);

        ResolutionResult resolutionResult = myConfiguration.getIncoming().getResolutionResult();
        extDependencies.addAll(dependencyResultsTransformer.buildExternalDependencies(resolutionResult.getRoot().getDependencies()));

        return new ExternalDepsResolutionResult(extDependencies,
                        new ArrayList<File>(dependencyResultsTransformer.getResolvedDepsFiles()));
    }

    protected Class<? extends Component> tryLoadingJvmLibraryClass() {
        Class<? extends Component> jvmLibrary = null;
        try {
            jvmLibrary = (Class<? extends Component>) Class.forName("org.gradle.jvm.JvmLibrary");
        }
        catch (ClassNotFoundException ignored) {
        }

        if (jvmLibrary == null) {
            try {
                jvmLibrary = (Class<? extends Component>) Class.forName("org.gradle.runtime.jvm.JvmLibrary");
            }
            catch (ClassNotFoundException ignored) {
            }
        }
        return jvmLibrary;
    }

    public Multimap<ModuleVersionIdentifier, ResolvedArtifact> groupByModuleVersionId(Set<ResolvedArtifact> resolvedArtifacts) {
        Multimap<ModuleVersionIdentifier, ResolvedArtifact> artifactMap = ArrayListMultimap.create();
        for (ResolvedArtifact artifact : resolvedArtifacts) {
            final ModuleVersionIdentifier moduleVersionId = artifact.getModuleVersion().getId();
            artifactMap.put(moduleVersionId, artifact);
        }
        return artifactMap;
    }

    public Map<ComponentIdentifier, ComponentArtifactsResult> buildAuxiliaryArtifactsMap(
                    final Class<? extends Component> jvmLibrary,
                    final Set<ResolvedArtifact> resolvedArtifacts) {
        List<ComponentIdentifier> components = new ArrayList<ComponentIdentifier>();
        for (ResolvedArtifact artifact : resolvedArtifacts) {
            final ModuleVersionIdentifier moduleVersionId = artifact.getModuleVersion().getId();
            if (!DependencyResolverImpl.isProjectDependencyArtifact(artifact)) {
                components.add(DependencyResolverImpl.toComponentIdentifier(moduleVersionId));
            }
        }

        boolean isBuildScriptConfiguration = myProject.getBuildscript().getConfigurations().contains(myConfiguration);
        DependencyHandler dependencyHandler = isBuildScriptConfiguration ? myProject.getBuildscript().getDependencies() : myProject.getDependencies();

        Set<ComponentArtifactsResult> componentResults = dependencyHandler.createArtifactResolutionQuery()
                                                                         .forComponents(components)
                                                                         .withArtifacts(jvmLibrary, additionalArtifactsTypes().toArray(new Class[0]))
                                                                         .execute()
                                                                         .getResolvedComponents();

        Map<ComponentIdentifier, ComponentArtifactsResult> componentResultsMap =
                        new HashMap<ComponentIdentifier, ComponentArtifactsResult>();

        for (ComponentArtifactsResult artifactsResult : componentResults) {
            componentResultsMap.put(artifactsResult.getId(), artifactsResult);
        }
        return componentResultsMap;
    }

    protected Collection<ExternalDependency> buildFileCollectionDeps(
                    Collection<ResolvedArtifact> resolvedArtifactToFilter,
                    Collection<ProjectDependency> projectDepsToFilter) {
        Collection<ExternalDependency> result = new ArrayList<ExternalDependency>();

        Set<File> fileDeps = new LinkedHashSet<File>(myConfiguration.getIncoming().getFiles().getFiles());
        for (ResolvedArtifact artifact : resolvedArtifactToFilter) {
            fileDeps.remove(artifact.getFile());
        }

        if (!fileDeps.isEmpty()) {
            for (ProjectDependency dep : projectDepsToFilter) {
                Configuration targetConfiguration = DependencyResolverImpl.getTargetConfiguration(dep);
                if (targetConfiguration == null) continue;
                Set<File> depFiles = targetConfiguration.getAllArtifacts().getFiles().getFiles();

                final Set<File> intersection = new LinkedHashSet<File>(Sets.intersection(fileDeps, depFiles));
                if (!intersection.isEmpty()) {
                    DefaultFileCollectionDependency fileCollectionDependency = new DefaultFileCollectionDependency(intersection);
                    fileCollectionDependency.setScope(myScope);
                    result.add(fileCollectionDependency);
                    fileDeps.removeAll(intersection);
                }
            }
        }

        for (File file : fileDeps) {
            DefaultFileCollectionDependency fileCollectionDependency = new DefaultFileCollectionDependency(Collections.singleton(file));
            fileCollectionDependency.setScope(myScope);
            result.add(fileCollectionDependency);
        }

        return result;
    }

    protected List<Class<? extends Artifact>> additionalArtifactsTypes() {
        List<Class<? extends Artifact>> artifactTypes = new ArrayList<Class<? extends Artifact>>();
        if (myDownloadSources) {
            artifactTypes.add(SourcesArtifact.class);
        }
        if (myDownloadJavadoc) {
            artifactTypes.add(JavadocArtifact.class);
        }
        return artifactTypes;
    }
}
