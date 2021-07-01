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
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.artifacts.Configuration;
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
	public static final class MixinInformationContainer {
		private final SourceSet sourceSet;
		private final String refmapName;
		private final String loomId;
		private final PatternSet mixinJsonPattern;

		private Stream<String> mixinJsonNames;

		public MixinInformationContainer(@NotNull SourceSet sourceSet,
										@NotNull String refmapName,
										@NotNull String loomId,
										@NotNull PatternSet mixinJsonPattern) {
			this.sourceSet = sourceSet;
			this.refmapName = refmapName;
			this.loomId = loomId;
			this.mixinJsonPattern = mixinJsonPattern;
		}

		private void setMixinJsonNames(@NotNull Stream<String> mixinJsonNames) {
			if (this.mixinJsonNames == null) {
				this.mixinJsonNames = mixinJsonNames;
			}
		}

		@NotNull
		public Stream<String> getMixinJsonNames() {
			return Objects.requireNonNull(mixinJsonNames);
		}

		public boolean isConfiguredByLoom(String loomId) {
			return this.loomId.equals(loomId);
		}

		@NotNull
		public SourceSet getSourceSet() {
			return sourceSet;
		}

		@NotNull
		public String getRefmapName() {
			return refmapName;
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

		if (extra.has(MIXIN_INFORMATION_CONTAINER)) {
			throw new InvalidUserDataException("The sourceSet " + sourceSet.getName()
					+ " has been configured for mixin annotation processor multiple times");
		}

		extra.set(MIXIN_INFORMATION_CONTAINER, container);
	}

	private PatternSet add0(SourceSet sourceSet, String refmapName) {
		PatternSet pattern = new PatternSet().setIncludes(Collections.singletonList("*.mixins.json"));
		setMixinInformationContainer(sourceSet, new MixinInformationContainer(sourceSet, refmapName, loomId, pattern));

		isDefault = false;

		return pattern;
	}

	/**
	 * Apply Mixin AP to sourceSet.
	 * @param sourceSet the sourceSet that applies Mixin AP
	 * @param refmapName the output ref-map name
	 */
	public void add(SourceSet sourceSet, String refmapName, Action<PatternSet> action) {
		PatternSet pattern = add0(sourceSet, refmapName);
		action.execute(pattern);
	}

	public void add(SourceSet sourceSet, String refmapName) {
		add(sourceSet, refmapName, x -> { });
	}

	public void add(String sourceSetName, String refmapName, Action<PatternSet> action) {
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

		PatternSet pattern = add0(sourceSet, refmapName);
		action.execute(pattern);
	}

	public void add(String sourceSetName, String refmapName) {
		add(sourceSetName, refmapName, x -> { });
	}

	/**
	 * Apply Mixin AP to sourceSet with output ref-map name equal to {@code loom.refmapName}.
	 * @param sourceSet the sourceSet that applies Mixin AP
	 */
	public void add(SourceSet sourceSet, Action<PatternSet> action) {
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		add(sourceSet, extension.getRefmapName(), action);
	}

	public void add(SourceSet sourceSet) {
		add(sourceSet, x -> { });
	}

	public void add(String sourceSetName, Action<PatternSet> action) {
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		add(sourceSetName, extension.getRefmapName(), action);
	}

	public void add(String sourceSetName) {
		add(sourceSetName, x -> { });
	}

	@NotNull
	public Stream<SourceSet> getSourceSets(Project project0) {
		return project0.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().stream()
				.filter(sourceSet -> {
					MixinInformationContainer container = getMixinInformationContainer(sourceSet);

					if (container != null && container.isConfiguredByLoom(loomId)) {
						PatternSet pattern = container.mixinJsonPattern;
						Stream<String> mixinJsonNames = sourceSet.getResources()
								.matching(pattern).getFiles().stream().map(File::getName);
						container.setMixinJsonNames(mixinJsonNames);
						return true;
					}

					return false;
				});
	}

	@NotNull
	public Stream<Configuration> getApConfigurations(Project project0, Function<String, String> getApConfigNameFunc) {
		return getSourceSets(project0)
				.map(sourceSet -> project0.getConfigurations().getByName(getApConfigNameFunc.apply(sourceSet.getName())));
	}

	@NotNull
	public Stream<Map.Entry<SourceSet, Task>> getInvokerTasks(Project project0, String compileTaskLanguage) {
		return getSourceSets(project0)
				.flatMap(sourceSet -> {
					try {
						Task task = project0.getTasks().getByName(sourceSet.getCompileTaskName(compileTaskLanguage));
						return Stream.of(new AbstractMap.SimpleEntry<>(sourceSet, task));
					} catch (UnknownTaskException ignored) {
						return Stream.empty();
					}
				});
	}

	public void init() {
		if (isDefault) {
			project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().forEach(this::add);
		}

		isDefault = false;
	}

	@NotNull
	@Input
	public Collection<SourceSet> getAllMixinSourceSets() {
		if (isCrossProject) {
			return project.getRootProject().getAllprojects().stream()
					.flatMap(this::getSourceSets).collect(Collectors.toList());
		} else {
			return getSourceSets(project).collect(Collectors.toList());
		}
	}
}
