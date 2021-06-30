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

package net.fabricmc.loom;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.SourceSet;
import org.jetbrains.annotations.NotNull;

/**
 * A gradle extension to configure mixin annotation processor.
 */
public class MixinAnnotationProcessorExtension {
	public static final String MIXIN_AP_CONFIG_CONTAINER = "mixin";

	private final Project project;
	private final String loomId;

	private boolean isAcrossProject;
	private final List<SourceSet> mixinSourceSets;

	/**
	 * A information container stores necessary information
	 * for configure mixin annotation processor. It stores
	 * in [SourceSet].ext.mixin.
	 */
	public static class MixinAPInfoContainer {
		private boolean initialized;

		private Project project;
		private final String loomId;

		private final String refmapName;
		private Collection<String> mixinJsonNames;

		public MixinAPInfoContainer(String refmapName, String loomId) {
			this.initialized = false;
			this.loomId = loomId;
			this.refmapName = refmapName;
		}

		public void init(Project project, Collection<String> mixinJsonNames) {
			if (this.initialized) {
				throw new IllegalStateException("Cannot re-init MixinAPInfoContainer for project"
						+ project.getName() + " and refmapName " + refmapName);
			}

			this.project = project;
			this.mixinJsonNames = mixinJsonNames;
			this.initialized = true;
		}

		public Project getProject() {
			return project;
		}

		public String getRefmapName() {
			return refmapName;
		}

		public Collection<String> getMixinJsonNames() {
			return Collections.unmodifiableCollection(mixinJsonNames);
		}

		public boolean isConfiguredByLoom(String loomId) {
			return this.loomId.equals(loomId);
		}
	}

	public MixinAnnotationProcessorExtension(Project project) {
		this.project = project;
		this.isAcrossProject = false;
		this.loomId = project.getPath() + ":fabric-loom";
		this.mixinSourceSets = new ArrayList<>();
	}

	/**
	 * Set true if you want to configure annotation processor for another project.
	 * Notice that this is highly discouraged. Use at your own risk.
	 * @param acrossProject a boolean
	 */
	public void setAcrossProject(boolean acrossProject) {
		isAcrossProject = acrossProject;
	}

	@Input
	public boolean getAcrossProject() {
		return isAcrossProject;
	}

	/**
	 * Apply Mixin AP to sourceSet.
	 * @param sourceSet the sourceSet that applies Mixin AP
	 * @param refmapName the output ref-map name
	 */
	public void add(SourceSet sourceSet, String refmapName) {
		sourceSet.getExtensions().getExtraProperties()
				.set("mixin", new MixinAPInfoContainer(refmapName, loomId));
		mixinSourceSets.add(sourceSet);
	}

	public void add(String sourceSetName, String refmapName) {
		// try to find sourceSet with name sourceSetName in this project
		SourceSet sourceSet = project.getConvention().getPlugin(JavaPluginConvention.class)
				.getSourceSets().findByName(sourceSetName);

		if (sourceSet == null) {
			if (isAcrossProject) {
				// let's try to find it in across all projects
				Stream<SourceSet> stream = project.getRootProject().getAllprojects().stream()
						.map(proj -> proj.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().findByName(sourceSetName));
				sourceSet = switch ((int) stream.count()) {
				// still no sourceSet found
				case 0 -> throw new InvalidUserDataException("No sourceSet " + sourceSetName + " was found");
				case 1 -> stream.findFirst().orElseThrow();
				// multiple sourceSet name found
				default -> throw new InvalidUserDataException("Ambiguous sourceSet name " + sourceSetName);
				};
			} else {
				throw new InvalidUserDataException("No sourceSet " + sourceSetName + " was found");
			}
		}

		add(sourceSet, refmapName);
	}

	/**
	 * Apply Mixin AP to sourceSet with output ref-map name equal to {@code loom.refmapName}.
	 * @param sourceSet the sourceSet that applies Mixin AP
	 */
	public void add(SourceSet sourceSet) {
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		add(sourceSet, extension.getRefmapName());
	}

	public void add(String sourceSetName) {
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		add(sourceSetName, extension.getRefmapName());
	}

	private void initForProject(Project project0) {
		project0.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().forEach(sourceSet -> {
			MixinAPInfoContainer container = (MixinAPInfoContainer) sourceSet.getExtensions()
					.getExtraProperties().get(MIXIN_AP_CONFIG_CONTAINER);
			if (container != null && container.isConfiguredByLoom(loomId)) {
				// only proceed if there is a MixinAPInfoContainer and is configured by this fabric-loom
				Collection<String> mixinJsonNames = sourceSet.getAllSource().getFiles().stream()
						.map(File::getPath)
						.filter(name -> name.matches(".*\\.mixin\\.json"))
						.collect(Collectors.toSet());
				container.init(project0, mixinJsonNames);
			}
		});
	}

	public void init() {
		if (isAcrossProject) {
			project.getLogger().warn("You set acrossProject = true for Mixin Annotation Processor. "
					+ "Please note this is highly discouraged and should not be used unless is necessary.");
			project.getRootProject().getAllprojects().forEach(this::initForProject);
		} else {
			initForProject(project);
		}
	}

	@NotNull
	@Input
	public Collection<SourceSet> getMixinSourceSets() {
		return Collections.unmodifiableCollection(mixinSourceSets);
	}
}
