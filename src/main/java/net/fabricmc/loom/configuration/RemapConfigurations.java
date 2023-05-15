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

package net.fabricmc.loom.configuration;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectList;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.attributes.Usage;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSet;
import org.jetbrains.annotations.VisibleForTesting;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.RemapConfigurationSettings;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.Strings;
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
			});
		}
	}

	/**
	 * Gets or creates the collector configuration for a {@link SourceSet}.
	 * The collector configuration receives all compile-time or runtime remapped mod dependency files.
	 *
	 * @param project  the project
	 * @param settings the remap configuration settings
	 * @param runtime  if {@code true}, returns the runtime configuration;
	 *                 if {@code false}, returns the compile-time one
	 * @return the collector configuration
	 */
	public static Configuration getOrCreateCollectorConfiguration(Project project, RemapConfigurationSettings settings, boolean runtime) {
		return getOrCreateCollectorConfiguration(project, settings.getSourceSet().get(), runtime);
	}

	/**
	 * Gets or creates the collector configuration for a {@link RemapConfigurationSettings} instance.
	 * The collector configuration receives all compile-time or runtime remapped mod dependency files.
	 *
	 * @param project   the project
	 * @param sourceSet the source set to apply the collector config to, should generally match {@link RemapConfigurationSettings#getSourceSet()}
	 * @param runtime   if {@code true}, returns the runtime configuration;
	 *                  if {@code false}, returns the compile-time one
	 * @return the collector configuration
	 */
	// Note: this method is generally called on demand, so these configurations
	// won't exist at buildscript evaluation time. There's no need for them anyway
	// since they're internals.
	public static Configuration getOrCreateCollectorConfiguration(Project project, SourceSet sourceSet, boolean runtime) {
		final String configurationName = "mod"
				+ (runtime ? "Runtime" : "Compile")
				+ "Classpath"
				+ Strings.capitalize(sourceSet.getName())
				+ "Mapped";
		final ConfigurationContainer configurations = project.getConfigurations();
		Configuration configuration = configurations.findByName(configurationName);

		if (configuration == null) {
			configuration = configurations.create(configurationName);

			// Don't get transitive deps of already remapped mods
			configuration.setTransitive(false);

			// Set the usage attribute to fetch the correct artifacts.
			// Note: Even though most deps are resolved via copies of mod* configurations,
			// non-remapped mods that get added straight to these collectors will need the attribute.
			final Usage usage = project.getObjects().named(Usage.class, runtime ? Usage.JAVA_RUNTIME : Usage.JAVA_API);
			configuration.attributes(attributes -> attributes.attribute(Usage.USAGE_ATTRIBUTE, usage));

			// The main classpath also applies to the test source set like with normal dependencies.
			final boolean isMainSourceSet = sourceSet.getName().equals("main");

			if (runtime) {
				extendsFrom(sourceSet.getRuntimeClasspathConfigurationName(), configuration, project);

				if (isMainSourceSet) {
					extendsFrom(JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME, configuration, project);
				}
			} else {
				extendsFrom(sourceSet.getCompileClasspathConfigurationName(), configuration, project);
				extendsFrom(Constants.Configurations.MOD_COMPILE_CLASSPATH_MAPPED, configuration, project);

				if (isMainSourceSet) {
					extendsFrom(JavaPlugin.TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME, configuration, project);
				}
			}
		}

		return configuration;
	}

	public static void applyToProject(Project project, RemapConfigurationSettings settings) {
		// No point bothering to make it lazily, gradle realises configurations right away.
		// <https://github.com/gradle/gradle/blob/v7.4.2/subprojects/plugins/src/main/java/org/gradle/api/plugins/BasePlugin.java#L104>
		final Configuration configuration = project.getConfigurations().create(settings.getName());
		configuration.setTransitive(true);

		if (settings.getOnCompileClasspath().get()) {
			extendsFrom(Constants.Configurations.MOD_COMPILE_CLASSPATH, configuration, project);
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

			// Publish only for the main source set.
			if (SourceSet.MAIN_SOURCE_SET_NAME.equals(sourceSet.getName())) {
				configuration.getPublishingMode().convention(publishingMode);
			}
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
