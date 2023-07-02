/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022-2023 FabricMC
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

package net.fabricmc.loom.configuration.providers.minecraft;

import java.util.List;
import java.util.function.BiConsumer;

import com.google.common.base.Preconditions;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSet;
import org.gradle.jvm.tasks.Jar;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.RemapConfigurations;
import net.fabricmc.loom.task.AbstractRemapJarTask;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.gradle.SourceSetHelper;

public abstract sealed class MinecraftSourceSets permits MinecraftSourceSets.Single, MinecraftSourceSets.Split {
	public static MinecraftSourceSets get(Project project) {
		return LoomGradleExtension.get(project).areEnvironmentSourceSetsSplit() ? Split.INSTANCE : Single.INSTANCE;
	}

	public abstract void applyDependencies(BiConsumer<String, String> consumer, List<String> targets);

	public abstract String getSourceSetForEnv(String env);

	protected abstract List<ConfigurationName> getConfigurations();

	public void evaluateSplit(Project project) {
		final LoomGradleExtension extension = LoomGradleExtension.get(project);
		Preconditions.checkArgument(extension.areEnvironmentSourceSetsSplit());

		Split.INSTANCE.evaluate(project);
	}

	public abstract void afterEvaluate(Project project);

	protected void createConfigurations(Project project) {
		final ConfigurationContainer configurations = project.getConfigurations();

		for (ConfigurationName configurationName : getConfigurations()) {
			configurations.register(configurationName.runtime(), configuration -> {
				configuration.setTransitive(false);
				configuration.extendsFrom(configurations.getByName(configurationName.mcLibsRuntimeName()));
				configuration.extendsFrom(configurations.getByName(Constants.Configurations.LOADER_DEPENDENCIES));
				configuration.extendsFrom(configurations.getByName(Constants.Configurations.LOOM_DEVELOPMENT_DEPENDENCIES));
			});

			configurations.register(configurationName.compile(), configuration -> {
				configuration.setTransitive(false);
				configuration.extendsFrom(configurations.getByName(configurationName.mcLibsCompileName()));
				configuration.extendsFrom(configurations.getByName(Constants.Configurations.LOADER_DEPENDENCIES));
			});
		}
	}

	protected void extendsFrom(Project project, String name, String extendsFrom) {
		final ConfigurationContainer configurations = project.getConfigurations();

		configurations.named(name, configuration -> {
			configuration.extendsFrom(configurations.getByName(extendsFrom));
		});
	}

	/**
	 * Used when we have a single source set, either with split or merged jars.
	 */
	public static final class Single extends MinecraftSourceSets {
		private static final ConfigurationName MINECRAFT_NAMED = new ConfigurationName(
				"minecraftNamed",
				Constants.Configurations.MINECRAFT_COMPILE_LIBRARIES,
				Constants.Configurations.MINECRAFT_RUNTIME_LIBRARIES
		);

		private static final Single INSTANCE = new Single();

		@Override
		public void applyDependencies(BiConsumer<String, String> consumer, List<String> targets) {
			for (String target : targets) {
				consumer.accept(MINECRAFT_NAMED.compile(), target);
				consumer.accept(MINECRAFT_NAMED.runtime(), target);
			}
		}

		@Override
		public String getSourceSetForEnv(String env) {
			return SourceSet.MAIN_SOURCE_SET_NAME;
		}

		@Override
		protected List<ConfigurationName> getConfigurations() {
			return List.of(MINECRAFT_NAMED);
		}

		@Override
		public void afterEvaluate(Project project) {
			// This is done in afterEvaluate as we need to be sure that split source sets was not enabled.
			createConfigurations(project);

			extendsFrom(project, JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME, MINECRAFT_NAMED.compile());
			extendsFrom(project, JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME, MINECRAFT_NAMED.runtime());
			extendsFrom(project, JavaPlugin.TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME, MINECRAFT_NAMED.compile());
			extendsFrom(project, JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME, MINECRAFT_NAMED.runtime());
		}
	}

	/**
	 * Used when we have a split client/common source set and split jars.
	 */
	public static final class Split extends MinecraftSourceSets {
		private static final ConfigurationName MINECRAFT_COMMON_NAMED = new ConfigurationName(
				"minecraftCommonNamed",
				Constants.Configurations.MINECRAFT_COMPILE_LIBRARIES,
				Constants.Configurations.MINECRAFT_RUNTIME_LIBRARIES
		);
		// Depends on the Minecraft client libraries.
		private static final ConfigurationName MINECRAFT_CLIENT_ONLY_NAMED = new ConfigurationName(
				"minecraftClientOnlyNamed",
				Constants.Configurations.MINECRAFT_CLIENT_COMPILE_LIBRARIES,
				Constants.Configurations.MINECRAFT_CLIENT_RUNTIME_LIBRARIES
		);

		public static final String CLIENT_ONLY_SOURCE_SET_NAME = "client";

		private static final Split INSTANCE = new Split();

		@Override
		public void applyDependencies(BiConsumer<String, String> consumer, List<String> targets) {
			Preconditions.checkArgument(targets.size() == 2);
			Preconditions.checkArgument(targets.contains("common"));
			Preconditions.checkArgument(targets.contains("clientOnly"));

			consumer.accept(MINECRAFT_COMMON_NAMED.runtime(), "common");
			consumer.accept(MINECRAFT_CLIENT_ONLY_NAMED.runtime(), "clientOnly");
			consumer.accept(MINECRAFT_COMMON_NAMED.compile(), "common");
			consumer.accept(MINECRAFT_CLIENT_ONLY_NAMED.compile(), "clientOnly");
		}

		@Override
		public String getSourceSetForEnv(String env) {
			return env.equals("client") ? CLIENT_ONLY_SOURCE_SET_NAME : SourceSet.MAIN_SOURCE_SET_NAME;
		}

		@Override
		protected List<ConfigurationName> getConfigurations() {
			return List.of(MINECRAFT_COMMON_NAMED, MINECRAFT_CLIENT_ONLY_NAMED);
		}

		// Called during evaluation, when the loom extension method is called.
		private void evaluate(Project project) {
			createConfigurations(project);
			final ConfigurationContainer configurations = project.getConfigurations();

			// Register our new client only source set, main becomes common only, with their respective jars.
			final SourceSet mainSourceSet = SourceSetHelper.getMainSourceSet(project);
			final SourceSet clientOnlySourceSet = SourceSetHelper.createSourceSet(CLIENT_ONLY_SOURCE_SET_NAME, project);

			// Add Minecraft to the main and client source sets.
			extendsFrom(project, mainSourceSet.getCompileClasspathConfigurationName(), MINECRAFT_COMMON_NAMED.compile());
			extendsFrom(project, mainSourceSet.getRuntimeClasspathConfigurationName(), MINECRAFT_COMMON_NAMED.runtime());
			extendsFrom(project, clientOnlySourceSet.getCompileClasspathConfigurationName(), MINECRAFT_CLIENT_ONLY_NAMED.compile());
			extendsFrom(project, clientOnlySourceSet.getRuntimeClasspathConfigurationName(), MINECRAFT_CLIENT_ONLY_NAMED.runtime());

			// Client source set depends on common.
			extendsFrom(project, MINECRAFT_CLIENT_ONLY_NAMED.runtime(), MINECRAFT_COMMON_NAMED.runtime());
			extendsFrom(project, MINECRAFT_CLIENT_ONLY_NAMED.compile(), MINECRAFT_COMMON_NAMED.compile());

			// Client annotation processor configuration extendsFrom "annotationProcessor"
			extendsFrom(project, clientOnlySourceSet.getAnnotationProcessorConfigurationName(), JavaPlugin.ANNOTATION_PROCESSOR_CONFIGURATION_NAME);

			clientOnlySourceSet.setCompileClasspath(
					clientOnlySourceSet.getCompileClasspath()
							.plus(mainSourceSet.getCompileClasspath())
							.plus(mainSourceSet.getOutput())
			);
			clientOnlySourceSet.setRuntimeClasspath(
					clientOnlySourceSet.getRuntimeClasspath()
							.plus(mainSourceSet.getRuntimeClasspath())
							.plus(mainSourceSet.getOutput())
			);

			RemapConfigurations.configureClientConfigurations(project, clientOnlySourceSet);

			// Include the client only output in the jars
			project.getTasks().named(mainSourceSet.getJarTaskName(), Jar.class).configure(jar -> {
				jar.from(clientOnlySourceSet.getOutput().getClassesDirs());
				jar.from(clientOnlySourceSet.getOutput().getResourcesDir());

				jar.dependsOn(project.getTasks().named(clientOnlySourceSet.getProcessResourcesTaskName()));
			});

			// Remap with the client compile classpath.
			project.getTasks().withType(AbstractRemapJarTask.class).configureEach(remapJarTask -> {
				remapJarTask.getClasspath().from(
						project.getConfigurations().getByName(clientOnlySourceSet.getCompileClasspathConfigurationName())
				);
			});

			// The sources task can be registered at a later time.
			project.getTasks().configureEach(task -> {
				if (!mainSourceSet.getSourcesJarTaskName().equals(task.getName()) || !(task instanceof Jar jar)) {
					// Not the sources task we are looking for.
					return;
				}

				// The client only sources to the combined sources jar.
				jar.from(clientOnlySourceSet.getAllSource());
			});

			project.getTasks().withType(AbstractRemapJarTask.class, task -> {
				// Set the default client only source set name
				task.getClientOnlySourceSetName().convention(CLIENT_ONLY_SOURCE_SET_NAME);
			});
		}

		@Override
		public void afterEvaluate(Project project) {
		}
	}

	private record ConfigurationName(String baseName, String mcLibsCompileName, String mcLibsRuntimeName) {
		private String runtime() {
			return baseName + "Runtime";
		}

		private String compile() {
			return baseName + "Compile";
		}
	}
}
