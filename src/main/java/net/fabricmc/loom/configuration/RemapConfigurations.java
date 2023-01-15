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

import java.util.Locale;
import java.util.function.Function;

import org.gradle.api.NamedDomainObjectList;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.tasks.SourceSet;
import org.jetbrains.annotations.VisibleForTesting;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.RemapConfigurationSettings;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.gradle.SourceSetHelper;

public final class RemapConfigurations {
	private RemapConfigurations() {
	}

	public static void defineConfigurationsForSourceSet(
			final SourceSet sourceSet,
			final Project project
	) {
		final String sourceSetName = sourceSet.toString();
		final ConfigurationContainer configurations = project.getConfigurations();

		final Configuration implementationConfiguration = configurations.maybeCreate(
				ConfigurationOption.name(sourceSet, sourceSet.getImplementationConfigurationName()));
		implementationConfiguration.setVisible(false);
		implementationConfiguration.setDescription("Implementation only mod dependencies for " + sourceSetName + ".");
		implementationConfiguration.setCanBeConsumed(false);
		implementationConfiguration.setCanBeResolved(false);

		final Configuration compileOnlyConfiguration = configurations.maybeCreate(
				ConfigurationOption.name(sourceSet, sourceSet.getCompileOnlyConfigurationName()));
		compileOnlyConfiguration.setVisible(false);
		compileOnlyConfiguration.setCanBeConsumed(false);
		compileOnlyConfiguration.setCanBeResolved(false);
		compileOnlyConfiguration.setDescription("Compile only mod dependencies for " + sourceSetName + ".");

		final Configuration compileClasspathConfiguration = configurations.maybeCreate(
				ConfigurationOption.name(sourceSet, sourceSet.getCompileClasspathConfigurationName()));
		compileClasspathConfiguration.setVisible(false);
		compileClasspathConfiguration.extendsFrom(compileOnlyConfiguration, implementationConfiguration);
		compileClasspathConfiguration.setDescription("Compile mod classpath for " + sourceSetName + ".");
		compileClasspathConfiguration.setCanBeConsumed(false);
		copyAttributes(configurations, sourceSet.getCompileClasspathConfigurationName(), compileClasspathConfiguration);

		final Configuration mappedCompileClasspathConfiguration = configurations.maybeCreate(
				ConfigurationOption.name(sourceSet, sourceSet.getCompileClasspathConfigurationName() + "Mapped"));
		mappedCompileClasspathConfiguration.setVisible(false);
		mappedCompileClasspathConfiguration.setDescription("Mapped compile mod classpath for " + sourceSetName + ".");
		mappedCompileClasspathConfiguration.setCanBeConsumed(false);
		mappedCompileClasspathConfiguration.setTransitive(false);
		copyAttributes(configurations, sourceSet.getCompileClasspathConfigurationName(), mappedCompileClasspathConfiguration);

		final Configuration runtimeOnlyConfiguration = configurations.maybeCreate(
				ConfigurationOption.name(sourceSet, sourceSet.getRuntimeOnlyConfigurationName()));
		runtimeOnlyConfiguration.setVisible(false);
		runtimeOnlyConfiguration.setCanBeConsumed(false);
		runtimeOnlyConfiguration.setCanBeResolved(false);
		runtimeOnlyConfiguration.setDescription("Runtime only mod dependencies for " + sourceSetName + ".");

		final Configuration runtimeClasspathConfiguration = configurations.maybeCreate(
				ConfigurationOption.name(sourceSet, sourceSet.getRuntimeClasspathConfigurationName()));
		runtimeClasspathConfiguration.setVisible(false);
		runtimeClasspathConfiguration.setCanBeConsumed(false);
		runtimeClasspathConfiguration.setCanBeResolved(true);
		runtimeClasspathConfiguration.setDescription("Runtime mod classpath of " + sourceSetName + ".");
		runtimeClasspathConfiguration.extendsFrom(runtimeOnlyConfiguration, implementationConfiguration);
		copyAttributes(configurations, sourceSet.getRuntimeClasspathConfigurationName(), runtimeClasspathConfiguration);

		final SourceSet mainSourceSet = SourceSetHelper.getMainSourceSet(project);

		if (sourceSet.getName().equals(mainSourceSet.getName())) {
			final Configuration localRuntimeConfiguration = configurations.maybeCreate(
					ConfigurationOption.name(sourceSet, Constants.Configurations.LOCAL_RUNTIME));
			localRuntimeConfiguration.setVisible(false);
			localRuntimeConfiguration.setCanBeConsumed(false);
			localRuntimeConfiguration.setCanBeResolved(false);
			localRuntimeConfiguration.setDescription("Local runtime dependencies for " + sourceSetName + ".");
			runtimeClasspathConfiguration.extendsFrom(localRuntimeConfiguration);
		}

		final Configuration mappedRuntimeClasspathConfiguration = configurations.maybeCreate(
				ConfigurationOption.name(sourceSet, sourceSet.getRuntimeClasspathConfigurationName() + "Mapped"));
		mappedRuntimeClasspathConfiguration.setVisible(false);
		mappedRuntimeClasspathConfiguration.setCanBeConsumed(false);
		mappedRuntimeClasspathConfiguration.setCanBeResolved(true);
		mappedRuntimeClasspathConfiguration.setDescription("Mapped runtime mod classpath of " + sourceSetName + ".");
		mappedRuntimeClasspathConfiguration.setTransitive(false);
		copyAttributes(configurations, sourceSet.getRuntimeClasspathConfigurationName(), mappedRuntimeClasspathConfiguration);

		configurations.named(sourceSet.getRuntimeClasspathConfigurationName()).configure(config -> config.extendsFrom(mappedRuntimeClasspathConfiguration));
		configurations.named(sourceSet.getCompileClasspathConfigurationName()).configure(config -> config.extendsFrom(mappedCompileClasspathConfiguration));

		if (sourceSet.getName().equals(mainSourceSet.getName()) || sourceSet.getName().equals("client")) {
			final Configuration apiConfiguration = configurations.maybeCreate(
					ConfigurationOption.name(sourceSet, sourceSet.getApiConfigurationName()));
			apiConfiguration.setVisible(false);
			apiConfiguration.setDescription("API mod dependencies for " + sourceSetName + ".");
			apiConfiguration.setCanBeConsumed(false);
			apiConfiguration.setCanBeResolved(false);
			implementationConfiguration.extendsFrom(apiConfiguration);

			final Configuration compileOnlyApiConfiguration = configurations.maybeCreate(
					ConfigurationOption.name(sourceSet, sourceSet.getCompileOnlyApiConfigurationName()));
			compileOnlyApiConfiguration.setVisible(false);
			compileOnlyApiConfiguration.setCanBeConsumed(false);
			compileOnlyApiConfiguration.setCanBeResolved(false);
			compileOnlyApiConfiguration.setDescription("Compile only API mod dependencies for " + sourceSetName + ".");
			compileOnlyConfiguration.extendsFrom(compileOnlyApiConfiguration);

			final Configuration apiElementsConfiguration = configurations.getByName(mainSourceSet.getApiElementsConfigurationName());
			apiElementsConfiguration.extendsFrom(apiConfiguration, compileOnlyApiConfiguration);

			final Configuration runtimeElementsConfiguration = configurations.getByName(mainSourceSet.getRuntimeElementsConfigurationName());
			runtimeElementsConfiguration.extendsFrom(runtimeOnlyConfiguration, implementationConfiguration);
		}

		final LoomGradleExtension extension = LoomGradleExtension.get(project);
		extension.addRemapConfiguration(sourceSet.getName(), config -> {
			config.getCompileClasspathConfiguration().set(compileClasspathConfiguration);
			config.getRuntimeClasspathConfiguration().set(runtimeClasspathConfiguration);
			config.getMappedCompileClasspathConfiguration().set(mappedCompileClasspathConfiguration);
			config.getMappedRuntimeClasspathConfiguration().set(mappedRuntimeClasspathConfiguration);
		});
	}

	private static void copyAttributes(final ConfigurationContainer configurations, final String from, final Configuration to) {
		final AttributeContainer fromAttr = configurations.getByName(from).getAttributes();

		for (final Attribute<?> key : fromAttr.keySet()) {
			to.getAttributes().attribute((Attribute) key, fromAttr.getAttribute(key));
		}
	}

	public static void configureClientConfigurations(Project project, SourceSet clientSourceSet) {
		final LoomGradleExtension extension = LoomGradleExtension.get(project);
		extension.createRemapConfigurations(clientSourceSet);

		final NamedDomainObjectList<RemapConfigurationSettings> remapConfigurations = extension.getRemapConfigurations();
		SourceSet mainSourceSet = SourceSetHelper.getMainSourceSet(project);
		final ConfigurationContainer configurations = project.getConfigurations();

		final Configuration mappedClientCompileClasspathConfiguration = configurations.maybeCreate(
				ConfigurationOption.name(mainSourceSet, mainSourceSet.getCompileClasspathConfigurationName() + "ClientMapped"));
		mappedClientCompileClasspathConfiguration.setVisible(false);
		mappedClientCompileClasspathConfiguration.setDescription("Mapped compile client mod classpath for " + mainSourceSet + ".");
		mappedClientCompileClasspathConfiguration.setCanBeConsumed(false);
		mappedClientCompileClasspathConfiguration.setTransitive(false);
		copyAttributes(configurations, mainSourceSet.getCompileClasspathConfigurationName(), mappedClientCompileClasspathConfiguration);

		final Configuration mappedClientRuntimeClasspathConfiguration = configurations.maybeCreate(
				ConfigurationOption.name(mainSourceSet, mainSourceSet.getRuntimeClasspathConfigurationName() + "ClientMapped"));
		mappedClientRuntimeClasspathConfiguration.setVisible(false);
		mappedClientRuntimeClasspathConfiguration.setCanBeConsumed(false);
		mappedClientRuntimeClasspathConfiguration.setCanBeResolved(true);
		mappedClientRuntimeClasspathConfiguration.setDescription("Mapped runtime client mod classpath of " + mainSourceSet + ".");
		mappedClientRuntimeClasspathConfiguration.setTransitive(false);
		copyAttributes(configurations, mainSourceSet.getRuntimeClasspathConfigurationName(), mappedClientRuntimeClasspathConfiguration);

		configurations.named(clientSourceSet.getRuntimeClasspathConfigurationName()).configure(config -> config.extendsFrom(mappedClientRuntimeClasspathConfiguration));
		configurations.named(clientSourceSet.getCompileClasspathConfigurationName()).configure(config -> config.extendsFrom(mappedClientCompileClasspathConfiguration));

		remapConfigurations.named(mainSourceSet.getName()).configure(config -> {
			config.getMappedClientCompileClasspathConfiguration().set(mappedClientCompileClasspathConfiguration);
			config.getMappedClientRuntimeClasspathConfiguration().set(mappedClientRuntimeClasspathConfiguration);
		});
	}

	private static String capitalise(String str) {
		return str.substring(0, 1).toUpperCase(Locale.ROOT) + str.substring(1);
	}

	@VisibleForTesting
	public record ConfigurationOption(Function<SourceSet, String> targetNameFunc, boolean compileClasspath, boolean runtimeClasspath, RemapConfigurationSettings.PublishingMode publishingMode) {
		String targetName(SourceSet sourceSet) {
			return targetNameFunc.apply(sourceSet);
		}

		public String name(SourceSet sourceSet) {
			String targetName = targetName(sourceSet);

			if (targetName == null) {
				throw new UnsupportedOperationException("Configuration option is not available for sourceset (%s)".formatted(sourceSet.getName()));
			}

			if (targetName.startsWith(sourceSet.getName())) {
				targetName = targetName.substring(sourceSet.getName().length());
			}

			return name(sourceSet, targetName);
		}

		public static String name(SourceSet sourceSet, String targetName) {
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
