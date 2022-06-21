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
import java.util.Map;
import java.util.function.Function;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSet;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.RemapConfigurationSettings;
import net.fabricmc.loom.util.Constants;

public final class RemapConfigurations {
	private static final String modApi = "modApi";
	private static final String modImplementation = "modImplementation";
	private static final String modCompileOnly = "modCompileOnly";
	private static final String modCompileOnlyApi = "modCompileOnlyApi";
	private static final String modRuntimeOnly = "modRuntimeOnly";
	private static final String modLocalRuntime = "modLocalRuntime";

	private static final Map<String, Function<SourceSet, String>> CONFIG_NAME_MAP = Map.of(
			modApi, SourceSet::getApiConfigurationName,
			modImplementation, SourceSet::getImplementationConfigurationName,
			modCompileOnly, SourceSet::getCompileOnlyConfigurationName,
			modCompileOnlyApi, SourceSet::getCompileOnlyApiConfigurationName,
			modRuntimeOnly, SourceSet::getRuntimeOnlyConfigurationName,
			modLocalRuntime, SourceSet::getRuntimeOnlyConfigurationName
	);

	private RemapConfigurations() {
	}

	public static void setupConfigurations(Project project) {
		final LoomGradleExtension extension = LoomGradleExtension.get(project);
		extension.addRemapConfiguration(modApi, configure(JavaPlugin.API_CONFIGURATION_NAME, true, true, RemapConfigurationSettings.PublishingMode.COMPILE_AND_RUNTIME));
		extension.addRemapConfiguration(modImplementation, configure(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME, true, true, RemapConfigurationSettings.PublishingMode.RUNTIME_ONLY));
		extension.addRemapConfiguration(modCompileOnly, configure(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME, true, false, RemapConfigurationSettings.PublishingMode.NONE));
		extension.addRemapConfiguration(modCompileOnlyApi, configure(JavaPlugin.COMPILE_ONLY_API_CONFIGURATION_NAME, true, false, RemapConfigurationSettings.PublishingMode.COMPILE_ONLY));
		extension.addRemapConfiguration(modRuntimeOnly, configure(JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME, false, true, RemapConfigurationSettings.PublishingMode.RUNTIME_ONLY));
		extension.addRemapConfiguration(modLocalRuntime, configure(Constants.Configurations.LOCAL_RUNTIME, false, true, RemapConfigurationSettings.PublishingMode.NONE));
	}

	public static void configureClientConfigurations(Project project, SourceSet clientSourceSet) {
		final LoomGradleExtension extension = LoomGradleExtension.get(project);
		final List<RemapConfigurationSettings> configurations = extension.getRemapConfigurations();

		for (RemapConfigurationSettings configuration : configurations) {
			Function<SourceSet, String> function = CONFIG_NAME_MAP.get(configuration.getName());

			if (function != null) {
				configuration.getClientTargetConfigurationName().set(function.apply(clientSourceSet));
			}
		}

		// TODO create client only versions of the configurations
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
}
