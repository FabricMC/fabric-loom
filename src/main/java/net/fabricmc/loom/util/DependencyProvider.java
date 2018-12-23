/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016, 2017, 2018 FabricMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.fabricmc.loom.util;

import com.google.common.collect.ImmutableSet;
import net.fabricmc.loom.LoomGradleExtension;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.artifacts.query.ArtifactResolutionQuery;
import org.gradle.api.artifacts.result.ArtifactResolutionResult;
import org.gradle.api.artifacts.result.ArtifactResult;
import org.gradle.api.artifacts.result.ComponentArtifactsResult;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;
import org.gradle.jvm.JvmLibrary;
import org.gradle.language.base.artifact.SourcesArtifact;

import java.io.File;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

public abstract class DependencyProvider {

	private LoomDependencyManager dependencyManager;

	public abstract void provide(DependencyInfo dependency, Project project, LoomGradleExtension extension, Consumer<Runnable> postPopulationScheduler) throws Exception;

	public abstract String getTargetConfig();

	public void addDependency(Object object, Project project) {
		addDependency(object, project, "compile");
	}

	public void addDependency(Object object, Project project, String target) {
		if(object instanceof File){
			object = project.files(object);
		}
		project.getDependencies().add(target, object);
	}

	public void register(LoomDependencyManager dependencyManager){
		this.dependencyManager = dependencyManager;
	}

	public LoomDependencyManager getDependencyManager() {
		return dependencyManager;
	}

	public static class DependencyInfo {
		final Project project;
		final Dependency dependency;
		final Configuration sourceConfiguration;

		public DependencyInfo(Project project, Dependency dependency, Configuration sourceConfiguration) {
			this.project = project;
			this.dependency = dependency;
			this.sourceConfiguration = sourceConfiguration;
		}

		public Dependency getDependency() {
			return dependency;
		}

		public String getResolvedVersion() {
			for (ResolvedDependency rd : sourceConfiguration.getResolvedConfiguration().getFirstLevelModuleDependencies()) {
				if (rd.getModuleGroup().equals(dependency.getGroup()) && rd.getModuleName().equals(dependency.getName())) {
					return rd.getModuleVersion();
				}
			}

			return dependency.getVersion();
		}

		public Configuration getSourceConfiguration() {
			return sourceConfiguration;
		}

		// TODO: Can this be done with stable APIs only?
		@SuppressWarnings("UnstableApiUsage")
		public Set<File> resolve(String classifier) {
			if (classifier.isEmpty()) {
				return sourceConfiguration.files(dependency);
			} else if ("sources".equals(classifier)) {
				for (ResolvedArtifact rd : sourceConfiguration.getResolvedConfiguration().getResolvedArtifacts()) {
					if (rd.getModuleVersion().getId().getGroup().equals(dependency.getGroup())
							&& rd.getModuleVersion().getId().getName().equals(dependency.getName())
							&& rd.getModuleVersion().getId().getVersion().equals(dependency.getVersion())) {

						ImmutableSet.Builder<File> files = ImmutableSet.builder();

						ArtifactResolutionQuery query = project.getDependencies().createArtifactResolutionQuery();
						query.forComponents(DefaultModuleComponentIdentifier.newId(rd.getModuleVersion().getId()));
						//noinspection unchecked
						query.withArtifacts(JvmLibrary.class, SourcesArtifact.class);
						for (ComponentArtifactsResult cresult : query.execute().getResolvedComponents()) {
							for (ArtifactResult result : cresult.getArtifacts(SourcesArtifact.class)) {
								if (result instanceof ResolvedArtifactResult) {
									files.add(((ResolvedArtifactResult) result).getFile());
								}
							}
						}

						return files.build();
					}
				}

				return ImmutableSet.of();
			} else {
				project.getLogger().warn("Unsupported classifier '" + classifier + "'");
				return ImmutableSet.of();
			}
		}

		public Optional<File> resolveFile() {
			return resolveFile("");
		}

		public Optional<File> resolveFile(String classifier) {
			Set<File> files = resolve(classifier);
			if (files.isEmpty()) {
				return Optional.empty();
			} else if (files.size() > 1) {
				StringBuilder builder = new StringBuilder(this.toString());
				if (!classifier.isEmpty()) {
					builder.append(" [").append(classifier).append("]");
				}
				builder.append(" resolves to more than one file:");
				for (File f : files) {
					builder.append("\n\t-").append(f.getAbsolutePath());
				}
				throw new RuntimeException(builder.toString());
			} else {
				return files.stream().findFirst();
			}
		}

		@Override
		public String toString() {
			return getDepString();
		}

		public String getDepString(){
			return dependency.getGroup() + ":" + dependency.getName() + ":" + dependency.getVersion();
		}

		public String getResolvedDepString(){
			return dependency.getGroup() + ":" + dependency.getName() + ":" + getResolvedVersion();
		}
	}
}
