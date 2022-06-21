/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022 FabricMC
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

package net.fabricmc.loom.configuration;

import java.util.List;
import java.util.function.Function;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSet;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.RemapConfigurationSettings;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.gradle.SourceSetHelper;

public final class RemapConfigurations {
	private static final String modApi = "modApi";
	private static final String modImplementation = "modImplementation";
	private static final String modCompileOnly = "modCompileOnly";
	private static final String modCompileOnlyApi = "modCompileOnlyApi";
	private static final String modRuntimeOnly = "modRuntimeOnly";
	private static final String modLocalRuntime = "modLocalRuntime";

	private static final List<ConfigurationOptions> OPTIONS = List.of(
			new ConfigurationOptions(modApi, mainOnly(SourceSet::getApiConfigurationName), true, true, RemapConfigurationSettings.PublishingMode.COMPILE_AND_RUNTIME),
			new ConfigurationOptions(modImplementation, SourceSet::getImplementationConfigurationName, true, true, RemapConfigurationSettings.PublishingMode.RUNTIME_ONLY),
			new ConfigurationOptions(modCompileOnly, SourceSet::getCompileOnlyConfigurationName, true, false, RemapConfigurationSettings.PublishingMode.NONE),
			new ConfigurationOptions(modCompileOnlyApi, mainOnly(SourceSet::getCompileOnlyApiConfigurationName), true, false, RemapConfigurationSettings.PublishingMode.COMPILE_ONLY),
			new ConfigurationOptions(modRuntimeOnly, SourceSet::getRuntimeOnlyConfigurationName, false, true, RemapConfigurationSettings.PublishingMode.RUNTIME_ONLY),
			new ConfigurationOptions(modLocalRuntime, mainOnly(Constants.Configurations.LOCAL_RUNTIME), false, true, RemapConfigurationSettings.PublishingMode.NONE)
	);

	private static final List<String> MAIN_CONFIGURATIONS = OPTIONS.stream().map(ConfigurationOptions::baseName).toList();

	private RemapConfigurations() {
	}

	public static void setupConfigurations(Project project) {
		setupForSourceSet(project, SourceSetHelper.getMainSourceSet(project));
	}

	// TODO expose this?
	private static void setupForSourceSet(Project project, SourceSet sourceSet) {
		final LoomGradleExtension extension = LoomGradleExtension.get(project);

		for (ConfigurationOptions option : OPTIONS) {
			String targetConfiguration = option.targetNameFunc.apply(sourceSet);

			if (targetConfiguration == null) {
				continue;
			}

			String name = option.baseName();

			if (!sourceSet.getName().equals(SourceSet.MAIN_SOURCE_SET_NAME)) {
				name = sourceSet.getName() + (name.substring(0, 1).toUpperCase() + name.substring(1));
			}

			extension.addRemapConfiguration(name, configure(
					targetConfiguration,
					option.compileClasspath(),
					option.runtimeClasspath(),
					option.publishingMode()
			));
		}
	}

	public static void configureClientConfigurations(Project project, SourceSet clientSourceSet) {
		setupForSourceSet(project, clientSourceSet);

		final LoomGradleExtension extension = LoomGradleExtension.get(project);
		final List<RemapConfigurationSettings> configurations = extension.getRemapConfigurations();

		// Apply the client target names to the main configurations
		for (RemapConfigurationSettings configuration : configurations) {
			if (!MAIN_CONFIGURATIONS.contains(configuration.getName())) {
				return;
			}

			final String clientTargetName = getOptionsByName(configuration.getName()).targetNameFunc.apply(clientSourceSet);
			configuration.getClientTargetConfigurationName().convention(clientTargetName);
		}
	}

	public static void applyToProject(Project project, RemapConfigurationSettings settings) {
		// No point bothering to make it lazily, gradle realises configurations right away.
		// <https://github.com/gradle/gradle/blob/v7.4.2/subprojects/plugins/src/main/java/org/gradle/api/plugins/BasePlugin.java#L104>
		final Configuration remappedConfiguration = project.getConfigurations().create(settings.getRemappedConfigurationName());
		final Configuration configuration = project.getConfigurations().create(settings.getName());
		configuration.setTransitive(true);
		// Don't get transitive deps of already remapped mods
		remappedConfiguration.setTransitive(false);

		if (settings.getOnCompileClasspath().get()) {
			extendsFrom(Constants.Configurations.MOD_COMPILE_CLASSPATH, configuration, project);
			extendsFrom(Constants.Configurations.MOD_COMPILE_CLASSPATH_MAPPED, remappedConfiguration, project);
			extendsFrom(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME, remappedConfiguration, project);
			extendsFrom(JavaPlugin.TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME, remappedConfiguration, project);
		}

		if (settings.getOnRuntimeClasspath().get()) {
			extendsFrom(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME, remappedConfiguration, project);
			extendsFrom(JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME, remappedConfiguration, project);
		}

		for (String outgoingConfigurationName : settings.getPublishingMode().get().outgoingConfigurations()) {
			extendsFrom(outgoingConfigurationName, configuration, project);
		}
	}

	private static Action<RemapConfigurationSettings> configure(String targetConfiguration, boolean compileClasspath, boolean runtimeClasspath, RemapConfigurationSettings.PublishingMode publishingMode) {
		return configuration -> {
			configuration.getTargetConfigurationName().convention(targetConfiguration);
			configuration.getOnCompileClasspath().convention(compileClasspath);
			configuration.getOnRuntimeClasspath().convention(runtimeClasspath);
			configuration.getPublishingMode().convention(publishingMode);
		};
	}

	private static void extendsFrom(String name, Configuration configuration, Project project) {
		project.getConfigurations().named(name).configure(namedConfiguration -> {
			namedConfiguration.extendsFrom(configuration);
		});
	}

	private static ConfigurationOptions getOptionsByName(String name) {
		return OPTIONS.stream().filter(options -> options.baseName.equals(name))
				.findFirst().orElseThrow();
	}

	private static Function<SourceSet, String> mainOnly(Function<SourceSet, String> function) {
		return sourceSet -> sourceSet.getName().equals(SourceSet.MAIN_SOURCE_SET_NAME) ? function.apply(sourceSet) : null;
	}

	private static Function<SourceSet, String> mainOnly(String name) {
		return mainOnly(sourceSet -> name);
	}

	private record ConfigurationOptions(String baseName, Function<SourceSet, String> targetNameFunc, boolean compileClasspath, boolean runtimeClasspath, RemapConfigurationSettings.PublishingMode publishingMode) { }
}
