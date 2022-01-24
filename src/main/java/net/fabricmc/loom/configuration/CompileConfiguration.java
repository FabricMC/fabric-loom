/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2021 FabricMC
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

import java.nio.charset.StandardCharsets;

import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.AbstractCopyTask;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.build.mixin.GroovyApInvoker;
import net.fabricmc.loom.build.mixin.JavaApInvoker;
import net.fabricmc.loom.build.mixin.KaptApInvoker;
import net.fabricmc.loom.build.mixin.ScalaApInvoker;
import net.fabricmc.loom.configuration.accesswidener.AccessWidenerJarProcessor;
import net.fabricmc.loom.configuration.accesswidener.TransitiveAccessWidenerJarProcessor;
import net.fabricmc.loom.configuration.ifaceinject.InterfaceInjectionProcessor;
import net.fabricmc.loom.configuration.processors.JarProcessorManager;
import net.fabricmc.loom.configuration.providers.mappings.MappingsProviderImpl;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftJarConfiguration;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftProvider;
import net.fabricmc.loom.configuration.providers.minecraft.mapped.IntermediaryMinecraftProvider;
import net.fabricmc.loom.configuration.providers.minecraft.mapped.NamedMinecraftProvider;
import net.fabricmc.loom.extension.MixinExtension;
import net.fabricmc.loom.util.Constants;

public final class CompileConfiguration {
	private CompileConfiguration() {
	}

	public static void setupConfigurations(Project project) {
		LoomGradleExtension extension = LoomGradleExtension.get(project);

		extension.createLazyConfiguration(Constants.Configurations.MOD_COMPILE_CLASSPATH, configuration -> configuration.setTransitive(true));
		extension.createLazyConfiguration(Constants.Configurations.MOD_COMPILE_CLASSPATH_MAPPED, configuration -> configuration.setTransitive(false));
		extension.createLazyConfiguration(Constants.Configurations.MINECRAFT_NAMED, configuration -> configuration.setTransitive(false)); // The launchers do not recurse dependencies
		NamedDomainObjectProvider<Configuration> serverDeps = extension.createLazyConfiguration(Constants.Configurations.MINECRAFT_SERVER_DEPENDENCIES, configuration -> configuration.setTransitive(false));
		extension.createLazyConfiguration(Constants.Configurations.MINECRAFT_RUNTIME_DEPENDENCIES, configuration -> configuration.setTransitive(false));
		extension.createLazyConfiguration(Constants.Configurations.MINECRAFT_DEPENDENCIES, configuration -> {
			configuration.extendsFrom(serverDeps.get());
			configuration.setTransitive(false);
		});
		extension.createLazyConfiguration(Constants.Configurations.MINECRAFT_NATIVES, configuration -> configuration.setTransitive(false));
		extension.createLazyConfiguration(Constants.Configurations.LOADER_DEPENDENCIES, configuration -> configuration.setTransitive(false));
		extension.createLazyConfiguration(Constants.Configurations.MINECRAFT, configuration -> configuration.setTransitive(false));
		extension.createLazyConfiguration(Constants.Configurations.INCLUDE, configuration -> configuration.setTransitive(false)); // Dont get transitive deps
		extension.createLazyConfiguration(Constants.Configurations.MAPPING_CONSTANTS);
		extension.createLazyConfiguration(Constants.Configurations.NAMED_ELEMENTS, configuration -> {
			configuration.setCanBeConsumed(true);
			configuration.setCanBeResolved(false);
			configuration.extendsFrom(project.getConfigurations().getByName(JavaPlugin.API_CONFIGURATION_NAME));
		});

		extendsFrom(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME, Constants.Configurations.MAPPING_CONSTANTS, project);

		extension.createLazyConfiguration(Constants.Configurations.MAPPINGS);
		extension.createLazyConfiguration(Constants.Configurations.MAPPINGS_FINAL);
		extension.createLazyConfiguration(Constants.Configurations.LOOM_DEVELOPMENT_DEPENDENCIES);
		extension.createLazyConfiguration(Constants.Configurations.UNPICK_CLASSPATH);
		extension.createLazyConfiguration(Constants.Configurations.LOCAL_RUNTIME);
		extendsFrom(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME, Constants.Configurations.LOCAL_RUNTIME, project);

		for (RemappedConfigurationEntry entry : Constants.MOD_COMPILE_ENTRIES) {
			extension.createLazyConfiguration(entry.sourceConfiguration())
					.configure(configuration -> configuration.setTransitive(true));

			// Don't get transitive deps of already remapped mods
			extension.createLazyConfiguration(entry.getRemappedConfiguration())
					.configure(configuration -> configuration.setTransitive(false));

			if (entry.compileClasspath()) {
				extendsFrom(Constants.Configurations.MOD_COMPILE_CLASSPATH, entry.sourceConfiguration(), project);
				extendsFrom(Constants.Configurations.MOD_COMPILE_CLASSPATH_MAPPED, entry.getRemappedConfiguration(), project);
				extendsFrom(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME, entry.getRemappedConfiguration(), project);
				extendsFrom(JavaPlugin.TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME, entry.getRemappedConfiguration(), project);
			}

			if (entry.runtimeClasspath()) {
				extendsFrom(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME, entry.getRemappedConfiguration(), project);
				extendsFrom(JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME, entry.getRemappedConfiguration(), project);
			}

			for (String outgoingConfiguration : entry.publishingMode().outgoingConfigurations()) {
				extendsFrom(outgoingConfiguration, entry.sourceConfiguration(), project);
			}
		}

		extendsFrom(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME, Constants.Configurations.MINECRAFT_NAMED, project);
		extendsFrom(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME, Constants.Configurations.MINECRAFT_NAMED, project);
		extendsFrom(JavaPlugin.TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME, Constants.Configurations.MINECRAFT_NAMED, project);
		extendsFrom(JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME, Constants.Configurations.MINECRAFT_NAMED, project);

		extendsFrom(Constants.Configurations.LOADER_DEPENDENCIES, Constants.Configurations.MINECRAFT_DEPENDENCIES, project);
		extendsFrom(Constants.Configurations.MINECRAFT_NAMED, Constants.Configurations.LOADER_DEPENDENCIES, project);

		extendsFrom(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME, Constants.Configurations.MAPPINGS_FINAL, project);
		extendsFrom(JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME, Constants.Configurations.MAPPINGS_FINAL, project);

		extendsFrom(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME, Constants.Configurations.LOOM_DEVELOPMENT_DEPENDENCIES, project);
		extendsFrom(JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME, Constants.Configurations.LOOM_DEVELOPMENT_DEPENDENCIES, project);

		extendsFrom(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME, Constants.Configurations.MINECRAFT_RUNTIME_DEPENDENCIES, project);

		// Add the dev time dependencies
		project.getDependencies().add(Constants.Configurations.LOOM_DEVELOPMENT_DEPENDENCIES, Constants.Dependencies.DEV_LAUNCH_INJECTOR + Constants.Dependencies.Versions.DEV_LAUNCH_INJECTOR);
		project.getDependencies().add(Constants.Configurations.LOOM_DEVELOPMENT_DEPENDENCIES, Constants.Dependencies.TERMINAL_CONSOLE_APPENDER + Constants.Dependencies.Versions.TERMINAL_CONSOLE_APPENDER);
		project.getDependencies().add(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME, Constants.Dependencies.JETBRAINS_ANNOTATIONS + Constants.Dependencies.Versions.JETBRAINS_ANNOTATIONS);
	}

	public static void configureCompile(Project p) {
		final JavaPluginExtension javaPluginExtension = p.getExtensions().getByType(JavaPluginExtension.class);
		LoomGradleExtension extension = LoomGradleExtension.get(p);

		p.getTasks().named(JavaPlugin.JAVADOC_TASK_NAME, Javadoc.class).configure(javadoc -> {
			final SourceSet main = javaPluginExtension.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
			javadoc.setClasspath(main.getOutput().plus(main.getCompileClasspath()));
		});

		p.afterEvaluate(project -> {
			try {
				setupMinecraft(project);
			} catch (Exception e) {
				throw new RuntimeException("Failed to setup minecraft", e);
			}

			LoomDependencyManager dependencyManager = new LoomDependencyManager();
			extension.setDependencyManager(dependencyManager);
			dependencyManager.handleDependencies(project);

			extension.getRemapArchives().finalizeValue();

			MixinExtension mixin = LoomGradleExtension.get(project).getMixin();

			if (mixin.getUseLegacyMixinAp().get()) {
				setupMixinAp(project, mixin);
			}

			configureDecompileTasks(project);
		});

		finalizedBy(p, "idea", "genIdeaWorkspace");
		finalizedBy(p, "eclipse", "genEclipseRuns");
		finalizedBy(p, "cleanEclipse", "cleanEclipseRuns");

		// Add the "dev" jar to the "namedElements" configuration
		p.artifacts(artifactHandler -> artifactHandler.add(Constants.Configurations.NAMED_ELEMENTS, p.getTasks().named("jar")));

		// Ensure that the encoding is set to UTF-8, no matter what the system default is
		// this fixes some edge cases with special characters not displaying correctly
		// see http://yodaconditions.net/blog/fix-for-java-file-encoding-problems-with-gradle.html
		p.getTasks().withType(AbstractCopyTask.class).configureEach(abstractCopyTask -> abstractCopyTask.setFilteringCharset(StandardCharsets.UTF_8.name()));
		p.getTasks().withType(JavaCompile.class).configureEach(javaCompile -> javaCompile.getOptions().setEncoding(StandardCharsets.UTF_8.name()));

		if (p.getPluginManager().hasPlugin("org.jetbrains.kotlin.kapt")) {
			// If loom is applied after kapt, then kapt will use the AP arguments too early for loom to pass the arguments we need for mixin.
			throw new IllegalArgumentException("fabric-loom must be applied BEFORE kapt in the plugins { } block.");
		}
	}

	// This is not thread safe across projects synchronize it here just to be sure, might be possible to move this further down, but for now this will do.
	private static synchronized void setupMinecraft(Project project) throws Exception {
		final LoomGradleExtension extension = LoomGradleExtension.get(project);
		final MinecraftJarConfiguration jarConfiguration = extension.getMinecraftJarConfiguration().get();

		// Provide the vanilla mc jars -- TODO share across projects.
		final MinecraftProvider minecraftProvider = jarConfiguration.getMinecraftProviderFunction().apply(project);
		extension.setMinecraftProvider(minecraftProvider);
		minecraftProvider.provide();

		final DependencyInfo mappingsDep = DependencyInfo.create(project, Constants.Configurations.MAPPINGS);
		final MappingsProviderImpl mappingsProvider = MappingsProviderImpl.getInstance(project, mappingsDep, minecraftProvider);
		extension.setMappingsProvider(mappingsProvider);
		mappingsProvider.applyToProject(project, mappingsDep);

		// Provide the remapped mc jars
		final IntermediaryMinecraftProvider<?> intermediaryMinecraftProvider = jarConfiguration.getIntermediaryMinecraftProviderBiFunction().apply(project, minecraftProvider);
		NamedMinecraftProvider<?> namedMinecraftProvider = jarConfiguration.getNamedMinecraftProviderBiFunction().apply(project, minecraftProvider);

		final JarProcessorManager jarProcessorManager = createJarProcessorManager(project);

		if (jarProcessorManager.active()) {
			// Wrap the named MC provider for one that will provide the processed jars
			namedMinecraftProvider = jarConfiguration.getProcessedNamedMinecraftProviderBiFunction().apply(namedMinecraftProvider, jarProcessorManager);
		}

		extension.setIntermediaryMinecraftProvider(intermediaryMinecraftProvider);
		intermediaryMinecraftProvider.provide(true);

		extension.setNamedMinecraftProvider(namedMinecraftProvider);
		namedMinecraftProvider.provide(true);
	}

	private static JarProcessorManager createJarProcessorManager(Project project) {
		final LoomGradleExtension extension = LoomGradleExtension.get(project);

		if (extension.getAccessWidenerPath().isPresent()) {
			extension.getGameJarProcessors().add(new AccessWidenerJarProcessor(project));
		}

		if (extension.getEnableTransitiveAccessWideners().get()) {
			TransitiveAccessWidenerJarProcessor transitiveAccessWidenerJarProcessor = new TransitiveAccessWidenerJarProcessor(project);

			if (!transitiveAccessWidenerJarProcessor.isEmpty()) {
				extension.getGameJarProcessors().add(transitiveAccessWidenerJarProcessor);
			}
		}

		if (extension.getEnableInterfaceInjection().get()) {
			InterfaceInjectionProcessor jarProcessor = new InterfaceInjectionProcessor(project);

			if (!jarProcessor.isEmpty()) {
				extension.getGameJarProcessors().add(jarProcessor);
			}
		}

		JarProcessorManager processorManager = new JarProcessorManager(extension.getGameJarProcessors().get());
		extension.setJarProcessorManager(processorManager);
		processorManager.setupProcessors();

		return processorManager;
	}

	private static void setupMixinAp(Project project, MixinExtension mixin) {
		mixin.init();

		// Disable some things used by log4j via the mixin AP that prevent it from being garbage collected
		System.setProperty("log4j2.disable.jmx", "true");
		System.setProperty("log4j.shutdownHookEnabled", "false");
		System.setProperty("log4j.skipJansi", "true");

		project.getLogger().info("Configuring compiler arguments for Java");

		new JavaApInvoker(project).configureMixin();

		if (project.getPluginManager().hasPlugin("scala")) {
			project.getLogger().info("Configuring compiler arguments for Scala");
			new ScalaApInvoker(project).configureMixin();
		}

		if (project.getPluginManager().hasPlugin("org.jetbrains.kotlin.kapt")) {
			project.getLogger().info("Configuring compiler arguments for Kapt plugin");
			new KaptApInvoker(project).configureMixin();
		}

		if (project.getPluginManager().hasPlugin("groovy")) {
			project.getLogger().info("Configuring compiler arguments for Groovy");
			new GroovyApInvoker(project).configureMixin();
		}
	}

	private static void configureDecompileTasks(Project project) {
		final LoomGradleExtension extension = LoomGradleExtension.get(project);

		extension.getMinecraftJarConfiguration().get().getDecompileConfigurationBiFunction()
				.apply(project, extension.getNamedMinecraftProvider()).afterEvaluation();
	}

	private static void extendsFrom(String a, String b, Project project) {
		project.getConfigurations().getByName(a, configuration -> configuration.extendsFrom(project.getConfigurations().getByName(b)));
	}

	private static void finalizedBy(Project project, String a, String b) {
		project.getTasks().named(a).configure(task -> task.finalizedBy(project.getTasks().named(b)));
	}
}
