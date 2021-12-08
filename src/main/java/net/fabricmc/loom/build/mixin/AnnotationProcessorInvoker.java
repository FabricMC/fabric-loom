/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2020-2021 FabricMC
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

package net.fabricmc.loom.build.mixin;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.tasks.SourceSet;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.ide.intelij.IntelijUtils;
import net.fabricmc.loom.extension.MixinExtension;
import net.fabricmc.loom.util.Constants;

/**
 * Normally javac invokes annotation processors, but when the scala or kapt plugin are installed they will want to invoke
 * the annotation processor themselves.
 * See Java and Kapt implementations for a more deep understanding of the things passed by the children.
 */
public abstract class AnnotationProcessorInvoker<T extends Task> {
	public static final String JAVA = "java";
	public static final String SCALA = "scala";

	protected final Project project;
	protected final Map<SourceSet, T> invokerTasks;
	private final Collection<Configuration> apConfigurations;

	protected AnnotationProcessorInvoker(Project project,
										Collection<Configuration> apConfigurations,
										Map<SourceSet, T> invokerTasks) {
		this.project = project;
		this.apConfigurations = apConfigurations;
		this.invokerTasks = invokerTasks;
	}

	protected static Collection<Configuration> getApConfigurations(Project project, Function<String, String> getApConfigNameFunc) {
		MixinExtension mixin = LoomGradleExtension.get(project).getMixin();
		return mixin.getApConfigurationsStream(getApConfigNameFunc).collect(Collectors.toList());
	}

	protected abstract void passArgument(T compileTask, String key, String value);

	protected abstract File getRefmapDestinationDir(T task);

	protected final String getRefmapDestination(T task, String refmapName) throws IOException {
		return new File(getRefmapDestinationDir(task), refmapName).getCanonicalPath();
	}

	private void passMixinArguments(T task, SourceSet sourceSet) {
		try {
			LoomGradleExtension loom = LoomGradleExtension.get(project);
			String refmapName = Objects.requireNonNull(MixinExtension.getMixinInformationContainer(sourceSet)).refmapNameProvider().get();
			Map<String, String> args = new HashMap<>() {{
					put(Constants.MixinArguments.IN_MAP_FILE_NAMED_INTERMEDIARY, loom.getMappingsProvider().tinyMappings.toFile().getCanonicalPath());
					put(Constants.MixinArguments.OUT_MAP_FILE_NAMED_INTERMEDIARY, loom.getNextMixinMappings().getCanonicalPath());
					put(Constants.MixinArguments.OUT_REFMAP_FILE, getRefmapDestination(task, refmapName));
					put(Constants.MixinArguments.DEFAULT_OBFUSCATION_ENV, "named:intermediary");
				}};

			project.getLogger().debug("Outputting refmap to dir: " + getRefmapDestinationDir(task) + " for compile task: " + task);
			args.forEach((k, v) -> passArgument(task, k, v));
		} catch (IOException e) {
			project.getLogger().error("Could not configure mixin annotation processors", e);
		}
	}

	public void configureMixin() {
		ConfigurationContainer configs = project.getConfigurations();

		if (!IntelijUtils.isIdeaSync()) {
			for (Configuration processorConfig : apConfigurations) {
				project.getLogger().info("Adding mixin to classpath of AP config: " + processorConfig.getName());
				// Pass named MC classpath to mixin AP classpath
				processorConfig.extendsFrom(
								configs.getByName(Constants.Configurations.MINECRAFT_NAMED),
								configs.getByName(Constants.Configurations.MOD_COMPILE_CLASSPATH_MAPPED),
								configs.getByName(Constants.Configurations.MAPPINGS_FINAL)
				);

				// Add Mixin and mixin extensions (fabric-mixin-compile-extensions pulls mixin itself too)
				project.getDependencies().add(processorConfig.getName(),
								Constants.Dependencies.MIXIN_COMPILE_EXTENSIONS + Constants.Dependencies.Versions.MIXIN_COMPILE_EXTENSIONS);
			}
		}

		for (Map.Entry<SourceSet, T> entry : invokerTasks.entrySet()) {
			passMixinArguments(entry.getValue(), entry.getKey());
		}
	}
}
