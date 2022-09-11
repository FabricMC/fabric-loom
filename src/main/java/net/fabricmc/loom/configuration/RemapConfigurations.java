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
import java.util.Locale;
import java.util.function.Function;

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectList;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSet;
import org.jetbrains.annotations.VisibleForTesting;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.RemapConfigurationSettings;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.gradle.SourceSetHelper;

public final class RemapConfigurations {
	private static final List<ConfigurationOption> OPTIONS = List.of(
			new ConfigurationOption(mainOnly(SourceSet::getApiConfigurationName), true, true, RemapConfigurationSettings.PublishingMode.COMPILE_AND_RUNTIME),
			new ConfigurationOption(SourceSet::getImplementationConfigurationName, true, true, RemapConfigurationSettings.PublishingMode.RUNTIME_ONLY),
			new ConfigurationOption(SourceSet::getCompileOnlyConfigurationName, true, false, RemapConfigurationSettings.PublishingMode.NONE),
			new ConfigurationOption(mainOnly(SourceSet::getCompileOnlyApiConfigurationName), true, false, RemapConfigurationSettings.PublishingMode.COMPILE_ONLY),
			new ConfigurationOption(SourceSet::getRuntimeOnlyConfigurationName, false, true, RemapConfigurationSettings.PublishingMode.RUNTIME_ONLY),
			new ConfigurationOption(mainOnly(Constants.Configurations.LOCAL_RUNTIME), false, true, RemapConfigurationSettings.PublishingMode.NONE)
	);

	private RemapConfigurations() {
	}

	public static void setupForSourceSet(Project project, SourceSet sourceSet) {
		final LoomGradleExtension extension = LoomGradleExtension.get(project);

		for (ConfigurationOption option : getValidOptions(sourceSet)) {
			extension.addRemapConfiguration(option.name(sourceSet), configure(
					sourceSet,
					option.targetName(sourceSet),
					option.compileClasspath(),
					option.runtimeClasspath(),
					option.publishingMode()
			));
		}
	}

	public static void configureClientConfigurations(Project project, SourceSet clientSourceSet) {
		final LoomGradleExtension extension = LoomGradleExtension.get(project);
		extension.createRemapConfigurations(clientSourceSet);

		final NamedDomainObjectList<RemapConfigurationSettings> configurations = extension.getRemapConfigurations();
		SourceSet mainSourceSet = SourceSetHelper.getMainSourceSet(project);

		// Apply the client target names to the main configurations
		for (ConfigurationOption option : getValidOptions(mainSourceSet)) {
			configurations.getByName(option.name(mainSourceSet), settings -> {
				String name = option.targetName(clientSourceSet);

				if (name == null) {
					return;
				}

				settings.getClientSourceConfigurationName().set(name);
				createClientMappedConfiguration(project, settings, clientSourceSet);
			});
		}
	}

	private static void createClientMappedConfiguration(Project project, RemapConfigurationSettings settings, SourceSet clientSourceSet) {
		final Configuration remappedConfiguration = project.getConfigurations().create(settings.getClientRemappedConfigurationName().get());
		// Don't get transitive deps of already remapped mods
		remappedConfiguration.setTransitive(false);

		if (settings.getOnCompileClasspath().get()) {
			extendsFrom(Constants.Configurations.MOD_COMPILE_CLASSPATH_MAPPED, remappedConfiguration, project);

			extendsFrom(clientSourceSet.getCompileClasspathConfigurationName(), remappedConfiguration, project);
		}

		if (settings.getOnRuntimeClasspath().get()) {
			extendsFrom(clientSourceSet.getRuntimeClasspathConfigurationName(), remappedConfiguration, project);
		}
	}

	public static void applyToProject(Project project, RemapConfigurationSettings settings) {
		final SourceSet sourceSet = settings.getSourceSet().get();
		final boolean isMainSourceSet = sourceSet.getName().equals("main");
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
			extendsFrom(sourceSet.getCompileClasspathConfigurationName(), remappedConfiguration, project);

			if (isMainSourceSet) {
				extendsFrom(JavaPlugin.TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME, remappedConfiguration, project);
			}
		}

		if (settings.getOnRuntimeClasspath().get()) {
			extendsFrom(sourceSet.getRuntimeClasspathConfigurationName(), remappedConfiguration, project);

			if (isMainSourceSet) {
				extendsFrom(JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME, remappedConfiguration, project);
			}
		}

		for (String outgoingConfigurationName : settings.getPublishingMode().get().outgoingConfigurations()) {
			extendsFrom(outgoingConfigurationName, configuration, project);
		}
	}

	private static Action<RemapConfigurationSettings> configure(SourceSet sourceSet, String targetConfiguration, boolean compileClasspath, boolean runtimeClasspath, RemapConfigurationSettings.PublishingMode publishingMode) {
		return configuration -> {
			configuration.getSourceSet().convention(sourceSet);
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

	private static Function<SourceSet, String> mainOnly(Function<SourceSet, String> function) {
		return sourceSet -> sourceSet.getName().equals(SourceSet.MAIN_SOURCE_SET_NAME) ? function.apply(sourceSet) : null;
	}

	private static Function<SourceSet, String> mainOnly(String name) {
		return mainOnly(sourceSet -> name);
	}

	private static List<ConfigurationOption> getValidOptions(SourceSet sourceSet) {
		return OPTIONS.stream().filter(option -> option.validFor(sourceSet)).toList();
	}

	private static String capitalise(String str) {
		return str.substring(0, 1).toUpperCase(Locale.ROOT) + str.substring(1);
	}

	@VisibleForTesting
	public record ConfigurationOption(Function<SourceSet, String> targetNameFunc, boolean compileClasspath, boolean runtimeClasspath, RemapConfigurationSettings.PublishingMode publishingMode) {
		String targetName(SourceSet sourceSet) {
			return targetNameFunc.apply(sourceSet);
		}

		boolean validFor(SourceSet sourceSet) {
			return targetName(sourceSet) != null;
		}

		public String name(SourceSet sourceSet) {
			String targetName = targetName(sourceSet);

			if (targetName == null) {
				throw new UnsupportedOperationException("Configuration option is not available for sourceset (%s)".formatted(sourceSet.getName()));
			}

			if (targetName.startsWith(sourceSet.getName())) {
				targetName = targetName.substring(sourceSet.getName().length());
			}

			final StringBuilder builder = new StringBuilder();
			builder.append("mod");

			if (!SourceSet.MAIN_SOURCE_SET_NAME.equals(sourceSet.getName())) {
				builder.append(capitalise(sourceSet.getName()));
			}

			builder.append(capitalise(targetName));
			return builder.toString();
		}
	}
}
