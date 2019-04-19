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
import com.google.common.collect.Iterables;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import net.fabricmc.loom.LoomGradleExtension;

import org.apache.commons.io.FilenameUtils;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.artifacts.SelfResolvingDependency;
import org.gradle.api.artifacts.query.ArtifactResolutionQuery;
import org.gradle.api.artifacts.result.ArtifactResult;
import org.gradle.api.artifacts.result.ComponentArtifactsResult;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;
import org.gradle.jvm.JvmLibrary;
import org.gradle.language.base.artifact.SourcesArtifact;
import org.zeroturnaround.zip.ZipUtil;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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

		public static DependencyInfo create(Project project, Dependency dependency, Configuration sourceConfiguration) {
			if (dependency instanceof SelfResolvingDependency) {
				return new FileDependencyInfo(project, (SelfResolvingDependency) dependency, sourceConfiguration);
			} else {
				return new DependencyInfo(project, dependency, sourceConfiguration);
			}
		}

		private DependencyInfo(Project project, Dependency dependency, Configuration sourceConfiguration) {
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

	public static class FileDependencyInfo extends DependencyInfo {
		protected final Map<String, File> classifierToFile = new HashMap<>();
		protected final String group = "net.fabricmc.synthetic", name, version;

		FileDependencyInfo(Project project, SelfResolvingDependency dependency, Configuration configuration) {
			super(project, dependency, configuration);

			Set<File> files = dependency.resolve();
			switch (files.size()) {
			case 0: //Don't think Gradle would ever let you do this
				throw new IllegalStateException("Empty dependency?");

			case 1: //Single file dependency
				classifierToFile.put("", Iterables.getOnlyElement(files));
				break;

			default: //File collection, try work out the classifiers
				List<File> sortedFiles = files.stream().sorted(Comparator.comparing(File::getName, Comparator.comparingInt(String::length))).collect(Collectors.toList());

				//First element in sortedFiles is the one with the shortest name, we presume all the others are different classifier types of this
				File shortest = sortedFiles.remove(0);
				String shortestName = FilenameUtils.removeExtension(shortest.getName()); //name.jar -> name

				for (File file : sortedFiles) {
					if (!file.getName().startsWith(shortestName)) {
						//If there is another file which doesn't start with the same name as the presumed classifier-less one we're out of our depth
						throw new IllegalArgumentException("Unable to resolve classifiers for " + this + " (failed to sort " + files + ')');
					}
				}

				//We appear to be right, therefore this is the normal dependency file we want
				classifierToFile.put("", shortest);

				int start = shortestName.length();
				for (File file : sortedFiles) {
					//Now we just have to work out what classifier type the other files are, this shouldn't even return an empty string
					String classifier = FilenameUtils.removeExtension(file.getName()).substring(start);

					//The classifier could well be separated with a dash (thing name.jar and name-sources.jar), we don't want that leading dash
					if (classifierToFile.put(classifier.charAt(0) == '-' ? classifier.substring(1) : classifier, file) != null) {
						throw new InvalidUserDataException("Duplicate classifiers for " + this + " (\"" + file.getName().substring(start) + "\" in " + files + ')');
					}
				}
			}

			File root = classifierToFile.get(""); //We've built the classifierToFile map, now to try find a name and version for our dependency
			if ("jar".equals(FilenameUtils.getExtension(root.getName())) && ZipUtil.containsEntry(root, "fabric.mod.json")) {
				//It's a Fabric mod, see how much we can extract out
				JsonObject json = new Gson().fromJson(new String(ZipUtil.unpackEntry(root, "fabric.mod.json"), StandardCharsets.UTF_8), JsonObject.class);
				if (json == null || !json.has("id") || !json.has("version")) throw new IllegalArgumentException("Invalid Fabric mod jar: " + root + " (malformed json: " + json + ')');

				if (json.has("name")) {//Go for the name field if it's got one
					name = json.get("name").getAsString();
				} else {
					name = json.get("id").getAsString();
				}
				version = json.get("version").getAsString();
			} else {
				//Not a Fabric mod, just have to make something up
				name = FilenameUtils.removeExtension(root.getName());
				version = "1.0";
			}
		}

		@Override
		public Set<File> resolve(String classifier) {
			File file = classifierToFile.get(classifier);
			if (file != null) return Collections.singleton(file);

			//Suppose we can always try the super resolving method, doubt it will do anything more though
			return super.resolve(classifier);
		}

		@Override
		public String getResolvedVersion() {
			return version;
		}

		@Override
		public String getDepString() {
			//Use our custom name and version with the dummy group rather than the null:unspecified:null it would otherwise return
			return group + ':' + name + ':' + version;
		}

		@Override
		public String getResolvedDepString() {
			return getDepString();
		}
	}
}
