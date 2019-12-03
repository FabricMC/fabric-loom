package net.fabricmc.loom.ide.idea;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import org.gradle.api.Action;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.ArtifactView;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.LenientConfiguration;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.result.ArtifactResolutionResult;
import org.gradle.api.artifacts.result.ArtifactResult;
import org.gradle.api.artifacts.result.ComponentArtifactsResult;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;
import org.gradle.api.component.Artifact;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.jvm.JvmLibrary;
import org.gradle.language.base.artifact.SourcesArtifact;
import org.gradle.language.java.artifact.JavadocArtifact;
import org.gradle.plugins.ide.internal.resolver.IdeDependencyVisitor;

public class CustomDependencyResolverDependencySet {
    private final DependencyHandler         dependencyHandler;
    private final Collection<Configuration> plusConfigurations;
    private final Collection<Configuration> minusConfigurations;

    public CustomDependencyResolverDependencySet(DependencyHandler dependencyHandler, Collection<Configuration> plusConfigurations, Collection<Configuration> minusConfigurations) {
        this.dependencyHandler = dependencyHandler;
        this.plusConfigurations = plusConfigurations;
        this.minusConfigurations = minusConfigurations;
    }

    public void visit(CustomDependencyResolvingDependenciesProvider.CustomDependencyResolvingDependenciesVisitor visitor) {
        if (plusConfigurations.isEmpty()) {
            return;
        }
        new CustomDependencyResolvingDependencyResult().visit(visitor);
    }

    private class CustomDependencyResolvingDependencyResult {
        private final Map<ComponentArtifactIdentifier, ResolvedArtifact>                                 resolvedArtifacts      = Maps.newLinkedHashMap();
        private final SetMultimap<ComponentArtifactIdentifier, Configuration>                                  configurations         = MultimapBuilder.hashKeys().linkedHashSetValues().build();
        private final Map<ComponentSelector, UnresolvedDependencyResult>                                       unresolvedDependencies = Maps.newLinkedHashMap();
        private final Table<ModuleComponentIdentifier, Class<? extends Artifact>, Set<ResolvedArtifactResult>> auxiliaryArtifacts     = HashBasedTable.create();

        public void visit(CustomDependencyResolvingDependenciesProvider.CustomDependencyResolvingDependenciesVisitor visitor) {
            resolvePlusConfigurations(visitor);
            resolveMinusConfigurations(visitor);
            resolveAuxiliaryArtifacts(visitor);
            visitArtifacts(visitor);
            visitUnresolvedDependencies(visitor);
        }

        private void resolvePlusConfigurations(CustomDependencyResolvingDependenciesProvider.CustomDependencyResolvingDependenciesVisitor visitor) {
            for (Configuration configuration : plusConfigurations) {
                Set<ResolvedDependency> artifacts = getResolvedArtifacts(configuration);
                for (ResolvedDependency resolvedArtifact : artifacts) {
                    for (final ResolvedArtifact allModuleArtifact : resolvedArtifact.getAllModuleArtifacts()) {
                        resolvedArtifacts.put(allModuleArtifact.getId(), allModuleArtifact);
                        configurations.put(allModuleArtifact.getId(), configuration);
                    }
                }
                for (UnresolvedDependencyResult unresolvedDependency : getUnresolvedDependencies(configuration, visitor)) {
                    unresolvedDependencies.put(unresolvedDependency.getAttempted(), unresolvedDependency);
                }
            }
        }

        private void resolveMinusConfigurations(CustomDependencyResolvingDependenciesProvider.CustomDependencyResolvingDependenciesVisitor visitor) {
            for (Configuration configuration : minusConfigurations) {
                Set<ResolvedDependency> artifacts = getResolvedArtifacts(configuration);
                for (ResolvedDependency resolvedArtifact : artifacts) {
                    for (final ResolvedArtifact allModuleArtifact : resolvedArtifact.getAllModuleArtifacts()) {
                        resolvedArtifacts.put(allModuleArtifact.getId(), allModuleArtifact);
                        configurations.put(allModuleArtifact.getId(), configuration);
                    }
                }
                for (UnresolvedDependencyResult unresolvedDependency : getUnresolvedDependencies(configuration, visitor)) {
                    unresolvedDependencies.remove(unresolvedDependency.getAttempted());
                }
            }
        }

        protected Set<ResolvedDependency> getResolvedArtifacts(Configuration configuration)
        {
            return configuration.getResolvedConfiguration().getLenientConfiguration().getAllModuleDependencies();
        }

        private Spec<ComponentIdentifier> getComponentFilter(IdeDependencyVisitor visitor) {
            return visitor.isOffline() ? NOT_A_MODULE : Specs.<ComponentIdentifier>satisfyAll();
        }

        private Iterable<UnresolvedDependencyResult> getUnresolvedDependencies(Configuration configuration, CustomDependencyResolvingDependenciesProvider.CustomDependencyResolvingDependenciesVisitor visitor) {
            if (visitor.isOffline()) {
                return Collections.emptySet();
            }
            return Iterables.filter(configuration.getIncoming().getResolutionResult().getRoot().getDependencies(), UnresolvedDependencyResult.class);
        }

        private void resolveAuxiliaryArtifacts(CustomDependencyResolvingDependenciesProvider.CustomDependencyResolvingDependenciesVisitor visitor) {
            if (visitor.isOffline()) {
                return;
            }

            Set<ModuleComponentIdentifier> componentIdentifiers = getModuleComponentIdentifiers();
            if (componentIdentifiers.isEmpty()) {
                return;
            }

            List<Class<? extends Artifact>> types = getAuxiliaryArtifactTypes(visitor);
            if (types.isEmpty()) {
                return;
            }



            ArtifactResolutionResult result = dependencyHandler.createArtifactResolutionQuery()
                                                              .forComponents(componentIdentifiers)
                                                              .withArtifacts(JvmLibrary.class, types)
                                                              .execute();

            for (ComponentArtifactsResult artifactsResult : result.getResolvedComponents()) {
                for (Class<? extends Artifact> type : types) {
                    Set<ResolvedArtifactResult> resolvedArtifactResults = Sets.newLinkedHashSet();

                    for (ArtifactResult artifactResult : artifactsResult.getArtifacts(type)) {
                        if (artifactResult instanceof ResolvedArtifactResult) {
                            resolvedArtifactResults.add((ResolvedArtifactResult) artifactResult);
                        }
                    }
                    auxiliaryArtifacts.put((ModuleComponentIdentifier) artifactsResult.getId(), type, resolvedArtifactResults);
                }
            }
        }

        private Set<ModuleComponentIdentifier> getModuleComponentIdentifiers() {
            Set<ModuleComponentIdentifier> componentIdentifiers = Sets.newLinkedHashSet();
            for (ComponentArtifactIdentifier identifier : resolvedArtifacts.keySet()) {
                ComponentIdentifier componentIdentifier = identifier.getComponentIdentifier();
                if (componentIdentifier instanceof ModuleComponentIdentifier) {
                    componentIdentifiers.add((ModuleComponentIdentifier) componentIdentifier);
                }
            }
            return componentIdentifiers;
        }

        private List<Class<? extends Artifact>> getAuxiliaryArtifactTypes(CustomDependencyResolvingDependenciesProvider.CustomDependencyResolvingDependenciesVisitor visitor) {
            List<Class<? extends Artifact>> types = Lists.newArrayListWithCapacity(2);
            if (visitor.downloadSources()) {
                types.add(SourcesArtifact.class);
            }
            if (visitor.downloadJavaDoc()) {
                types.add(JavadocArtifact.class);
            }
            return types;
        }

        private void visitArtifacts(CustomDependencyResolvingDependenciesProvider.CustomDependencyResolvingDependenciesVisitor visitor) {
            for (ResolvedArtifact artifact : resolvedArtifacts.values()) {
                ComponentIdentifier componentIdentifier = artifact.getId().getComponentIdentifier();
                ComponentArtifactIdentifier artifactIdentifier = artifact.getId();
                if (componentIdentifier instanceof ProjectComponentIdentifier) {
                    visitor.visitProjectDependency(artifact);
                } else if (componentIdentifier instanceof ModuleComponentIdentifier) {
                    Set<ResolvedArtifactResult> sources = auxiliaryArtifacts.get(componentIdentifier, SourcesArtifact.class);
                    sources = sources != null ? sources : Collections.<ResolvedArtifactResult>emptySet();
                    Set<ResolvedArtifactResult> javaDoc = auxiliaryArtifacts.get(componentIdentifier, JavadocArtifact.class);
                    javaDoc = javaDoc != null ? javaDoc : Collections.<ResolvedArtifactResult>emptySet();
                    visitor.visitModuleDependency(artifact, sources, javaDoc, isTestConfiguration(configurations.get(artifactIdentifier)));
                } else {
                    visitor.visitFileDependency(artifact, isTestConfiguration(configurations.get(artifactIdentifier)));
                }
            }
        }

        private boolean isTestConfiguration(Set<Configuration> configurations) {
            for (Configuration c : configurations) {
                if (!c.getName().toLowerCase().contains("test")) {
                    return false;
                }
            }
            return true;
        }

        private void visitUnresolvedDependencies(CustomDependencyResolvingDependenciesProvider.CustomDependencyResolvingDependenciesVisitor visitor) {
            for (UnresolvedDependencyResult unresolvedDependency : unresolvedDependencies.values()) {
                visitor.visitUnresolvedDependency(unresolvedDependency);
            }
        }
    }

    private static final Spec<ComponentIdentifier> NOT_A_MODULE = new Spec<ComponentIdentifier>() {
        @Override
        public boolean isSatisfiedBy(ComponentIdentifier id) {
            return !(id instanceof ModuleComponentIdentifier);
        }
    };
}
