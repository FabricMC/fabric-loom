/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2023 FabricMC
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.AbstractCopyTask;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.InterfaceInjectionExtensionAPI;
import net.fabricmc.loom.build.mixin.GroovyApInvoker;
import net.fabricmc.loom.build.mixin.JavaApInvoker;
import net.fabricmc.loom.build.mixin.KaptApInvoker;
import net.fabricmc.loom.build.mixin.ScalaApInvoker;
import net.fabricmc.loom.configuration.accesswidener.AccessWidenerJarProcessor;
import net.fabricmc.loom.configuration.ifaceinject.InterfaceInjectionProcessor;
import net.fabricmc.loom.configuration.processors.MinecraftJarProcessorManager;
import net.fabricmc.loom.configuration.processors.ModJavadocProcessor;
import net.fabricmc.loom.configuration.providers.mappings.MappingConfiguration;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftJarConfiguration;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftProvider;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftSourceSets;
import net.fabricmc.loom.configuration.providers.minecraft.mapped.IntermediaryMinecraftProvider;
import net.fabricmc.loom.configuration.providers.minecraft.mapped.NamedMinecraftProvider;
import net.fabricmc.loom.extension.MixinExtension;
import net.fabricmc.loom.util.Checksum;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.ExceptionUtil;
import net.fabricmc.loom.util.gradle.GradleUtils;
import net.fabricmc.loom.util.gradle.SourceSetHelper;
import net.fabricmc.loom.util.service.ScopedSharedServiceManager;
import net.fabricmc.loom.util.service.SharedServiceManager;

public final class CompileConfiguration {
	private CompileConfiguration() {
	}

	public static void setupConfigurations(Project project) {
		final ConfigurationContainer configurations = project.getConfigurations();
		final LoomGradleExtension extension = LoomGradleExtension.get(project);

		configurations.register(Constants.Configurations.MOD_COMPILE_CLASSPATH, configuration -> configuration.setTransitive(true));
		configurations.register(Constants.Configurations.MOD_COMPILE_CLASSPATH_MAPPED, configuration -> configuration.setTransitive(false));
		NamedDomainObjectProvider<Configuration> serverDeps = configurations.register(Constants.Configurations.MINECRAFT_SERVER_DEPENDENCIES, configuration -> configuration.setTransitive(false));
		configurations.register(Constants.Configurations.MINECRAFT_RUNTIME_DEPENDENCIES, configuration -> configuration.setTransitive(false));
		configurations.register(Constants.Configurations.MINECRAFT_DEPENDENCIES, configuration -> {
			configuration.extendsFrom(serverDeps.get());
			configuration.setTransitive(false);
		});
		configurations.register(Constants.Configurations.LOADER_DEPENDENCIES, configuration -> configuration.setTransitive(false));
		configurations.register(Constants.Configurations.MINECRAFT, configuration -> configuration.setTransitive(false));
		configurations.register(Constants.Configurations.INCLUDE, configuration -> configuration.setTransitive(false)); // Dont get transitive deps
		configurations.register(Constants.Configurations.MAPPING_CONSTANTS);
		configurations.register(Constants.Configurations.NAMED_ELEMENTS, configuration -> {
			configuration.setCanBeConsumed(true);
			configuration.setCanBeResolved(false);
			configuration.extendsFrom(configurations.getByName(JavaPlugin.API_CONFIGURATION_NAME));
		});

		extendsFrom(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME, Constants.Configurations.MAPPING_CONSTANTS, project);

		configurations.register(Constants.Configurations.MAPPINGS);
		configurations.register(Constants.Configurations.MAPPINGS_FINAL);
		configurations.register(Constants.Configurations.LOOM_DEVELOPMENT_DEPENDENCIES);
		configurations.register(Constants.Configurations.UNPICK_CLASSPATH);
		configurations.register(Constants.Configurations.LOCAL_RUNTIME);
		extendsFrom(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME, Constants.Configurations.LOCAL_RUNTIME, project);

		extension.createRemapConfigurations(SourceSetHelper.getMainSourceSet(project));

		extendsFrom(Constants.Configurations.LOADER_DEPENDENCIES, Constants.Configurations.MINECRAFT_DEPENDENCIES, project);

		extendsFrom(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME, Constants.Configurations.MAPPINGS_FINAL, project);
		extendsFrom(JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME, Constants.Configurations.MAPPINGS_FINAL, project);

		extendsFrom(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME, Constants.Configurations.LOOM_DEVELOPMENT_DEPENDENCIES, project);
		extendsFrom(JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME, Constants.Configurations.LOOM_DEVELOPMENT_DEPENDENCIES, project);

		extendsFrom(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME, Constants.Configurations.MINECRAFT_RUNTIME_DEPENDENCIES, project);

		// Add the dev time dependencies
		project.getDependencies().add(Constants.Configurations.LOOM_DEVELOPMENT_DEPENDENCIES, Constants.Dependencies.DEV_LAUNCH_INJECTOR + Constants.Dependencies.Versions.DEV_LAUNCH_INJECTOR);
		project.getDependencies().add(Constants.Configurations.LOOM_DEVELOPMENT_DEPENDENCIES, Constants.Dependencies.TERMINAL_CONSOLE_APPENDER + Constants.Dependencies.Versions.TERMINAL_CONSOLE_APPENDER);
		project.getDependencies().add(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME, Constants.Dependencies.JETBRAINS_ANNOTATIONS + Constants.Dependencies.Versions.JETBRAINS_ANNOTATIONS);
		project.getDependencies().add(JavaPlugin.TEST_COMPILE_ONLY_CONFIGURATION_NAME, Constants.Dependencies.JETBRAINS_ANNOTATIONS + Constants.Dependencies.Versions.JETBRAINS_ANNOTATIONS);
	}

	public static void configureCompile(Project project) {
		LoomGradleExtension extension = LoomGradleExtension.get(project);

		project.getTasks().named(JavaPlugin.JAVADOC_TASK_NAME, Javadoc.class).configure(javadoc -> {
			final SourceSet main = SourceSetHelper.getMainSourceSet(project);
			javadoc.setClasspath(main.getOutput().plus(main.getCompileClasspath()));
		});

		afterEvaluationWithService(project, (serviceManager) -> {
			final ConfigContext configContext = new ConfigContextImpl(project, serviceManager, extension);

			MinecraftSourceSets.get(project).afterEvaluate(project);

			final boolean previousRefreshDeps = extension.refreshDeps();

			if (getAndLock(project)) {
				project.getLogger().lifecycle("Found existing cache lock file, rebuilding loom cache. This may have been caused by a failed or canceled build.");
				extension.setRefreshDeps(true);
			}

			try {
				setupMinecraft(configContext);
			} catch (Exception e) {
				throw ExceptionUtil.createDescriptiveWrapper(RuntimeException::new, "Failed to setup Minecraft", e);
			}

			LoomDependencyManager dependencyManager = new LoomDependencyManager();
			extension.setDependencyManager(dependencyManager);
			dependencyManager.handleDependencies(project, serviceManager);

			releaseLock(project);
			extension.setRefreshDeps(previousRefreshDeps);

			MixinExtension mixin = LoomGradleExtension.get(project).getMixin();

			if (mixin.getUseLegacyMixinAp().get()) {
				setupMixinAp(project, mixin);
			}

			configureDecompileTasks(configContext);

			JUnitConfiguration.setup(project);
		});

		finalizedBy(project, "idea", "genIdeaWorkspace");
		finalizedBy(project, "eclipse", "genEclipseRuns");
		finalizedBy(project, "cleanEclipse", "cleanEclipseRuns");

		// Add the "dev" jar to the "namedElements" configuration
		project.artifacts(artifactHandler -> artifactHandler.add(Constants.Configurations.NAMED_ELEMENTS, project.getTasks().named("jar")));

		// Ensure that the encoding is set to UTF-8, no matter what the system default is
		// this fixes some edge cases with special characters not displaying correctly
		// see http://yodaconditions.net/blog/fix-for-java-file-encoding-problems-with-gradle.html
		project.getTasks().withType(AbstractCopyTask.class).configureEach(abstractCopyTask -> abstractCopyTask.setFilteringCharset(StandardCharsets.UTF_8.name()));
		project.getTasks().withType(JavaCompile.class).configureEach(javaCompile -> javaCompile.getOptions().setEncoding(StandardCharsets.UTF_8.name()));

		if (project.getPluginManager().hasPlugin("org.jetbrains.kotlin.kapt")) {
			// If loom is applied after kapt, then kapt will use the AP arguments too early for loom to pass the arguments we need for mixin.
			throw new IllegalArgumentException("fabric-loom must be applied BEFORE kapt in the plugins { } block.");
		}
	}

	// This is not thread safe across projects synchronize it here just to be sure, might be possible to move this further down, but for now this will do.
	private static synchronized void setupMinecraft(ConfigContext configContext) throws Exception {
		final Project project = configContext.project();
		final LoomGradleExtension extension = configContext.extension();
		final MinecraftJarConfiguration jarConfiguration = extension.getMinecraftJarConfiguration().get();

		// Provide the vanilla mc jars -- TODO share across projects.
		final MinecraftProvider minecraftProvider = jarConfiguration.getMinecraftProviderFunction().apply(configContext);
		extension.setMinecraftProvider(minecraftProvider);
		minecraftProvider.provide();

		final DependencyInfo mappingsDep = DependencyInfo.create(project, Constants.Configurations.MAPPINGS);
		final MappingConfiguration mappingConfiguration = MappingConfiguration.create(project, configContext.serviceManager(), mappingsDep, minecraftProvider);
		extension.setMappingConfiguration(mappingConfiguration);
		mappingConfiguration.applyToProject(project, mappingsDep);

		// Provide the remapped mc jars
		final IntermediaryMinecraftProvider<?> intermediaryMinecraftProvider = jarConfiguration.getIntermediaryMinecraftProviderBiFunction().apply(configContext, minecraftProvider);
		NamedMinecraftProvider<?> namedMinecraftProvider = jarConfiguration.getNamedMinecraftProviderBiFunction().apply(configContext, minecraftProvider);

		registerGameProcessors(configContext);
		MinecraftJarProcessorManager minecraftJarProcessorManager = MinecraftJarProcessorManager.create(project);

		if (minecraftJarProcessorManager != null) {
			// Wrap the named MC provider for one that will provide the processed jars
			namedMinecraftProvider = jarConfiguration.getProcessedNamedMinecraftProviderBiFunction().apply(namedMinecraftProvider, minecraftJarProcessorManager);
		}

		extension.setIntermediaryMinecraftProvider(intermediaryMinecraftProvider);
		intermediaryMinecraftProvider.provide(true);

		extension.setNamedMinecraftProvider(namedMinecraftProvider);
		namedMinecraftProvider.provide(true);
	}

	private static void registerGameProcessors(ConfigContext configContext) {
		final LoomGradleExtension extension = configContext.extension();

		final boolean enableTransitiveAccessWideners = extension.getEnableTransitiveAccessWideners().get();
		extension.addMinecraftJarProcessor(AccessWidenerJarProcessor.class, "fabric-loom:access-widener", enableTransitiveAccessWideners, extension.getAccessWidenerPath());

		if (extension.getEnableModProvidedJavadoc().get()) {
			extension.addMinecraftJarProcessor(ModJavadocProcessor.class, "fabric-loom:mod-javadoc");
		}

		final InterfaceInjectionExtensionAPI interfaceInjection = extension.getInterfaceInjection();

		if (interfaceInjection.isEnabled()) {
			extension.addMinecraftJarProcessor(InterfaceInjectionProcessor.class, "fabric-loom:interface-inject", interfaceInjection.getEnableDependencyInterfaceInjection().get());
		}
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

	private static void configureDecompileTasks(ConfigContext configContext) {
		final LoomGradleExtension extension = configContext.extension();

		extension.getMinecraftJarConfiguration().get().getDecompileConfigurationBiFunction()
				.apply(configContext, extension.getNamedMinecraftProvider()).afterEvaluation();
	}

	private static Path getLockFile(Project project) {
		final LoomGradleExtension extension = LoomGradleExtension.get(project);
		final Path cacheDirectory = extension.getFiles().getUserCache().toPath();
		final String pathHash = Checksum.projectHash(project);
		return cacheDirectory.resolve("." + pathHash + ".lock");
	}

	private static boolean getAndLock(Project project) {
		final Path lock = getLockFile(project);

		if (Files.exists(lock)) {
			return true;
		}

		try {
			Files.createFile(lock);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to acquire project configuration lock", e);
		}

		return false;
	}

	private static void releaseLock(Project project) {
		final Path lock = getLockFile(project);

		if (!Files.exists(lock)) {
			return;
		}

		try {
			Files.delete(lock);
		} catch (IOException e1) {
			try {
				// If we failed to delete the lock file, moving it before trying to delete it may help.
				final Path del = lock.resolveSibling(lock.getFileName() + ".del");
				Files.move(lock, del);
				Files.delete(del);
			} catch (IOException e2) {
				var exception = new UncheckedIOException("Failed to release project configuration lock", e2);
				exception.addSuppressed(e1);
				throw exception;
			}
		}
	}

	public static void extendsFrom(List<String> parents, String b, Project project) {
		for (String parent : parents) {
			extendsFrom(parent, b, project);
		}
	}

	public static void extendsFrom(String a, String b, Project project) {
		project.getConfigurations().getByName(a, configuration -> configuration.extendsFrom(project.getConfigurations().getByName(b)));
	}

	private static void finalizedBy(Project project, String a, String b) {
		project.getTasks().named(a).configure(task -> task.finalizedBy(project.getTasks().named(b)));
	}

	private static void afterEvaluationWithService(Project project, Consumer<SharedServiceManager> consumer) {
		GradleUtils.afterSuccessfulEvaluation(project, () -> {
			try (var serviceManager = new ScopedSharedServiceManager()) {
				consumer.accept(serviceManager);
			}
		});
	}
}
