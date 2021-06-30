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
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.util.PatternSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A gradle extension to configure mixin annotation processor.
 */
public class MixinAnnotationProcessorExtension {
	public static final String MIXIN_INFORMATION_CONTAINER = "mixin";
	private boolean isDefault;

	private final Project project;
	private final String loomId;
	private boolean isCrossProject;

	/**
	 * A information container stores necessary information
	 * for configure mixin annotation processor. It stores
	 * in [SourceSet].ext.mixin.
	 */
	public record MixinInformationContainer(SourceSet sourceSet, String refmapName,
											String loomId,
											PatternSet mixinJsonPattern) {
		@NotNull
		public Collection<String> getMixinJson() {
			return sourceSet.getAllSource().matching(mixinJsonPattern).getFiles().stream().map(File::getPath).collect(Collectors.toSet());
		}

		public boolean isConfiguredByLoom(String loomId) {
			return this.loomId.equals(loomId);
		}
	}

	public MixinAnnotationProcessorExtension(Project project) {
		this.isDefault = true;

		this.project = project;
		this.isCrossProject = false;
		this.loomId = project.getPath().equals(":") ? ":fabric-loom" : project.getPath() + ":fabric-loom";
	}

	/**
	 * Set true if you want to configure annotation processor for another project.
	 * Notice that this is highly discouraged. Use at your own risk.
	 * @param crossProject a boolean
	 */
	public void setIsCrossProject(boolean crossProject) {
		isCrossProject = crossProject;
	}

	@Input
	public boolean getIsCrossProject() {
		return isCrossProject;
	}

	@Nullable
	public static MixinInformationContainer getMixinInformationContainer(SourceSet sourceSet) {
		ExtraPropertiesExtension extra = sourceSet.getExtensions().getExtraProperties();
		return extra.has(MIXIN_INFORMATION_CONTAINER) ? (MixinInformationContainer) extra.get(MIXIN_INFORMATION_CONTAINER) : null;
	}

	public static void setMixinInformationContainer(SourceSet sourceSet, MixinInformationContainer container) {
		ExtraPropertiesExtension extra = sourceSet.getExtensions().getExtraProperties();
		extra.set(MIXIN_INFORMATION_CONTAINER, container);
	}

	private PatternSet add0(SourceSet sourceSet, String refmapName) {
		this.isDefault = false;
		PatternSet pattern = new PatternSet().setIncludes(Collections.singletonList("*.mixin.json"));
		setMixinInformationContainer(sourceSet, new MixinInformationContainer(sourceSet, refmapName, loomId, pattern));
		return pattern;
	}

	/**
	 * Apply Mixin AP to sourceSet.
	 * @param sourceSet the sourceSet that applies Mixin AP
	 * @param refmapName the output ref-map name
	 */
	public PatternSet add(SourceSet sourceSet, String refmapName) {
		return add0(sourceSet, refmapName);
	}

	public PatternSet add(String sourceSetName, String refmapName) {
		// try to find sourceSet with name sourceSetName in this project
		SourceSet sourceSet = project.getConvention().getPlugin(JavaPluginConvention.class)
				.getSourceSets().findByName(sourceSetName);

		if (sourceSet == null) {
			if (isCrossProject) {
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

		return add0(sourceSet, refmapName);
	}

	/**
	 * Apply Mixin AP to sourceSet with output ref-map name equal to {@code loom.refmapName}.
	 * @param sourceSet the sourceSet that applies Mixin AP
	 */
	public PatternSet add(SourceSet sourceSet) {
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		return add(sourceSet, extension.getRefmapName());
	}

	public PatternSet add(String sourceSetName) {
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		return add(sourceSetName, extension.getRefmapName());
	}

	private Stream<SourceSet> getMixinSourceSets(Project project0) {
		return project0.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().stream()
				.filter(sourceSet -> {
					MixinInformationContainer container = getMixinInformationContainer(sourceSet);
					return container != null && container.isConfiguredByLoom(loomId);
				});
	}

	@NotNull
	@Input
	public Collection<SourceSet> getMixinSourceSets() {
		if (!isDefault) {
			project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().forEach(this::add);
		}

		if (isCrossProject) {
			project.getLogger().warn("You set isCrossProject = true for Mixin Annotation Processor. "
					+ "This will apply the mixin annotation processor for the sourceSet you added outside of this project. "
					+ "Please note this is highly discouraged and should not be used unless is necessary.");

			return project.getRootProject().getAllprojects().stream()
					.flatMap(this::getMixinSourceSets).collect(Collectors.toList());
		} else {
			return getMixinSourceSets(project).collect(Collectors.toList());
		}
	}
}
