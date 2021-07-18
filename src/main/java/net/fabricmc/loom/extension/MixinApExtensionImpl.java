/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021 FabricMC
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

package net.fabricmc.loom.extension;

import java.io.File;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.util.PatternSet;
import org.jetbrains.annotations.NotNull;

public class MixinApExtensionImpl extends MixinApExtensionApiImpl implements MixinApExtension {
	private boolean isDefault;
	private final Project project;

	public MixinApExtensionImpl(Project project) {
		this.isDefault = true;
		this.project = project;
	}

	@Override
	public Project getProject() {
		return this.project;
	}

	@Override
	protected PatternSet add0(SourceSet sourceSet, String refmapName) {
		PatternSet pattern = new PatternSet().setIncludes(Collections.singletonList("*.mixins.json"));
		MixinApExtension.setMixinInformationContainer(sourceSet, new MixinApExtension.MixinInformationContainer(sourceSet, refmapName, pattern));

		isDefault = false;

		return pattern;
	}

	@Override
	@NotNull
	public Stream<SourceSet> getMixinSourceSetsStream() {
		return project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().stream()
				.filter(sourceSet -> {
					MixinApExtension.MixinInformationContainer container = MixinApExtension.getMixinInformationContainer(sourceSet);

					if (container != null) {
						PatternSet pattern = container.mixinJsonPattern;
						Stream<String> mixinJsonNames = sourceSet.getResources()
								.matching(pattern).getFiles().stream().map(File::getName);
						container.setMixinJsonNames(mixinJsonNames);
						return true;
					}

					return false;
				});
	}

	@Override
	@NotNull
	public Stream<Configuration> getApConfigurationsStream(Function<String, String> getApConfigNameFunc) {
		return getMixinSourceSetsStream()
				.map(sourceSet -> project.getConfigurations().getByName(getApConfigNameFunc.apply(sourceSet.getName())));
	}

	@Override
	@NotNull
	public Stream<Map.Entry<SourceSet, Task>> getInvokerTasksStream(String compileTaskLanguage) {
		return getMixinSourceSetsStream()
				.flatMap(sourceSet -> {
					try {
						Task task = project.getTasks().getByName(sourceSet.getCompileTaskName(compileTaskLanguage));
						return Stream.of(new AbstractMap.SimpleEntry<>(sourceSet, task));
					} catch (UnknownTaskException ignored) {
						return Stream.empty();
					}
				});
	}

	@Override
	@NotNull
	@Input
	public Collection<SourceSet> getMixinSourceSets() {
		return getMixinSourceSetsStream().collect(Collectors.toList());
	}

	@Override
	public void init() {
		if (isDefault) {
			project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().forEach(this::add);
		}

		isDefault = false;
	}
}
