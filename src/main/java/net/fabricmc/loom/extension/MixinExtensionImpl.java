/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021-2025 FabricMC
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

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.BasePluginExtension;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.util.PatternSet;
import org.jetbrains.annotations.NotNull;

public class MixinExtensionImpl extends MixinExtensionApiImpl implements MixinExtension {
	private boolean isDefault;
	private final Property<String> defaultRefmapName;

	@Inject
	public MixinExtensionImpl(Project project) {
		super(project);
		this.isDefault = true;
		this.defaultRefmapName = project.getObjects().property(String.class)
				.convention(project.provider(this::getDefaultMixinRefmapName));
		this.defaultRefmapName.finalizeValueOnRead();
	}

	@Override
	public Property<String> getDefaultRefmapName() {
		if (!super.getUseLegacyMixinAp().get()) throw new IllegalStateException("You need to set useLegacyMixinAp = true to configure Mixin annotation processor.");

		return defaultRefmapName;
	}

	private String getDefaultMixinRefmapName() {
		String defaultRefmapName = project.getExtensions().getByType(BasePluginExtension.class).getArchivesName().get() + "-refmap.json";
		project.getLogger().info("Could not find refmap definition, will be using default name: " + defaultRefmapName);
		return defaultRefmapName;
	}

	@Override
	protected PatternSet add0(SourceSet sourceSet, Provider<String> refmapName) {
		if (!super.getUseLegacyMixinAp().get()) throw new IllegalStateException("You need to set useLegacyMixinAp = true to configure Mixin annotation processor.");

		PatternSet pattern = new PatternSet().setIncludes(Collections.singletonList("**/*.json"));
		MixinExtension.setMixinInformationContainer(sourceSet, new MixinExtension.MixinInformationContainer(sourceSet, refmapName, pattern));

		isDefault = false;

		return pattern;
	}

	@Override
	@NotNull
	public Stream<SourceSet> getMixinSourceSetsStream() {
		return project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets().stream()
				.filter(sourceSet -> MixinExtension.getMixinInformationContainer(sourceSet) != null);
	}

	@Override
	@NotNull
	public Stream<Configuration> getApConfigurationsStream(Function<SourceSet, String> getApConfigNameFunc) {
		return getMixinSourceSetsStream()
				.map(sourceSet -> project.getConfigurations().getByName(getApConfigNameFunc.apply(sourceSet)));
	}

	@Override
	@NotNull
	public <T extends Task> Stream<Map.Entry<SourceSet, TaskProvider<T>>> getInvokerTasksStream(String compileTaskLanguage, Class<T> taskType) {
		return getMixinSourceSetsStream()
				.flatMap(sourceSet -> {
					try {
						TaskProvider<T> task = project.getTasks().named(sourceSet.getCompileTaskName(compileTaskLanguage), taskType);
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
			initDefault();
		}

		isDefault = false;
	}

	private void initDefault() {
		project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets().forEach(sourceSet -> {
			if (sourceSet.getName().equals("main")) {
				add(sourceSet);
			} else {
				add(sourceSet, getDefaultRefmapName().map(defaultRefmapName -> "%s-%s".formatted(sourceSet.getName(), defaultRefmapName)), x -> { });
			}
		});
	}
}
