/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016, 2017, 2018 FabricMC
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

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.javadoc.Javadoc;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.build.mixin.JavaApInvoker;
import net.fabricmc.loom.build.mixin.KaptApInvoker;
import net.fabricmc.loom.build.mixin.ScalaApInvoker;
import net.fabricmc.loom.configuration.ide.SetupIntelijRunConfigs;
import net.fabricmc.loom.configuration.providers.LaunchProvider;
import net.fabricmc.loom.configuration.providers.MinecraftProvider;
import net.fabricmc.loom.configuration.providers.forge.FieldMigratedMappingsProvider;
import net.fabricmc.loom.configuration.providers.forge.ForgeProvider;
import net.fabricmc.loom.configuration.providers.forge.ForgeUniversalProvider;
import net.fabricmc.loom.configuration.providers.forge.ForgeUserdevProvider;
import net.fabricmc.loom.configuration.providers.forge.McpConfigProvider;
import net.fabricmc.loom.configuration.providers.forge.PatchProvider;
import net.fabricmc.loom.configuration.providers.forge.SrgProvider;
import net.fabricmc.loom.configuration.providers.mappings.MappingsProvider;
import net.fabricmc.loom.task.GenVsCodeProjectTask;
import net.fabricmc.loom.util.Constants;

public final class CompileConfiguration {
	private CompileConfiguration() {
	}

	public static void setupConfigurations(Project project) {
		Configuration modCompileClasspathConfig = project.getConfigurations().maybeCreate(Constants.Configurations.MOD_COMPILE_CLASSPATH);
		modCompileClasspathConfig.setTransitive(true);
		Configuration modCompileClasspathMappedConfig = project.getConfigurations().maybeCreate(Constants.Configurations.MOD_COMPILE_CLASSPATH_MAPPED);
		modCompileClasspathMappedConfig.setTransitive(false);

		Configuration minecraftNamedConfig = project.getConfigurations().maybeCreate(Constants.Configurations.MINECRAFT_NAMED);
		minecraftNamedConfig.setTransitive(false); // The launchers do not recurse dependencies
		Configuration minecraftDependenciesConfig = project.getConfigurations().maybeCreate(Constants.Configurations.MINECRAFT_DEPENDENCIES);
		minecraftDependenciesConfig.setTransitive(false);
		Configuration loaderDependenciesConfig = project.getConfigurations().maybeCreate(Constants.Configurations.LOADER_DEPENDENCIES);
		loaderDependenciesConfig.setTransitive(false);
		Configuration minecraftConfig = project.getConfigurations().maybeCreate(Constants.Configurations.MINECRAFT);
		minecraftConfig.setTransitive(false);

		project.afterEvaluate(project1 -> {
			if (project.getExtensions().getByType(LoomGradleExtension.class).shouldGenerateSrgTiny()) {
				Configuration srg = project.getConfigurations().maybeCreate(Constants.Configurations.SRG);
				srg.setTransitive(false);
			}

			if (project.getExtensions().getByType(LoomGradleExtension.class).isDataGenEnabled()) {
				project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().getByName("main").resources(files -> {
					files.srcDir(project.file("src/generated/resources"));
				});
			}
		});

		if (project.getExtensions().getByType(LoomGradleExtension.class).isForge()) {
			Configuration forgeConfig = project.getConfigurations().maybeCreate(Constants.Configurations.FORGE);
			forgeConfig.setTransitive(false);
			Configuration forgeUserdevConfig = project.getConfigurations().maybeCreate(Constants.Configurations.FORGE_USERDEV);
			forgeUserdevConfig.setTransitive(false);
			Configuration forgeInstallerConfig = project.getConfigurations().maybeCreate(Constants.Configurations.FORGE_INSTALLER);
			forgeInstallerConfig.setTransitive(false);
			Configuration forgeUniversalConfig = project.getConfigurations().maybeCreate(Constants.Configurations.FORGE_UNIVERSAL);
			forgeUniversalConfig.setTransitive(false);
			Configuration forgeDependencies = project.getConfigurations().maybeCreate(Constants.Configurations.FORGE_DEPENDENCIES);
			forgeDependencies.setTransitive(false);
			Configuration mcpConfig = project.getConfigurations().maybeCreate(Constants.Configurations.MCP_CONFIG);
			mcpConfig.setTransitive(false);

			extendsFrom(Constants.Configurations.MINECRAFT_DEPENDENCIES, Constants.Configurations.FORGE_DEPENDENCIES, project);
		}

		if (project.getExtensions().getByType(LoomGradleExtension.class).supportsInclude()) {
			Configuration includeConfig = project.getConfigurations().maybeCreate(Constants.Configurations.INCLUDE);
			includeConfig.setTransitive(false); // Dont get transitive deps
		}

		project.getConfigurations().maybeCreate(Constants.Configurations.MAPPING_CONSTANTS);
		extendsFrom(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME, Constants.Configurations.MAPPING_CONSTANTS, project);

		project.getConfigurations().maybeCreate(Constants.Configurations.MAPPINGS);
		project.getConfigurations().maybeCreate(Constants.Configurations.MAPPINGS_FINAL);
		project.getConfigurations().maybeCreate(Constants.Configurations.LOOM_DEVELOPMENT_DEPENDENCIES);
		project.getConfigurations().maybeCreate(Constants.Configurations.UNPICK_CLASSPATH);

		for (RemappedConfigurationEntry entry : Constants.MOD_COMPILE_ENTRIES) {
			Configuration compileModsConfig = project.getConfigurations().maybeCreate(entry.getSourceConfiguration());
			compileModsConfig.setTransitive(true);
			Configuration compileModsMappedConfig = project.getConfigurations().maybeCreate(entry.getRemappedConfiguration());
			compileModsMappedConfig.setTransitive(false); // Don't get transitive deps of already remapped mods

			extendsFrom(entry.getTargetConfiguration(project.getConfigurations()), entry.getRemappedConfiguration(), project);

			if (entry.isOnModCompileClasspath()) {
				extendsFrom(Constants.Configurations.MOD_COMPILE_CLASSPATH, entry.getSourceConfiguration(), project);
				extendsFrom(Constants.Configurations.MOD_COMPILE_CLASSPATH_MAPPED, entry.getRemappedConfiguration(), project);
			}
		}

		extendsFrom(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME, Constants.Configurations.MINECRAFT_NAMED, project);
		extendsFrom(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME, Constants.Configurations.MINECRAFT_NAMED, project);
		extendsFrom(JavaPlugin.TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME, Constants.Configurations.MINECRAFT_NAMED, project);
		extendsFrom(JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME, Constants.Configurations.MINECRAFT_NAMED, project);

		extendsFrom(Constants.Configurations.LOADER_DEPENDENCIES, Constants.Configurations.MINECRAFT_DEPENDENCIES, project);
		extendsFrom(Constants.Configurations.MINECRAFT_NAMED, Constants.Configurations.LOADER_DEPENDENCIES, project);

		extendsFrom(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME, Constants.Configurations.MAPPINGS_FINAL, project);

		extendsFrom(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME, Constants.Configurations.LOOM_DEVELOPMENT_DEPENDENCIES, project);
		extendsFrom(JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME, Constants.Configurations.LOOM_DEVELOPMENT_DEPENDENCIES, project);
	}

	public static void configureCompile(Project p) {
		JavaPluginConvention javaModule = (JavaPluginConvention) p.getConvention().getPlugins().get("java");

		SourceSet main = javaModule.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);

		Javadoc javadoc = (Javadoc) p.getTasks().getByName(JavaPlugin.JAVADOC_TASK_NAME);
		javadoc.setClasspath(main.getOutput().plus(main.getCompileClasspath()));

		p.afterEvaluate(project -> {
			LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);

			MavenConfiguration.setup(project);

			LoomDependencyManager dependencyManager = new LoomDependencyManager();
			extension.setDependencyManager(dependencyManager);

			dependencyManager.addProvider(new MinecraftProvider(project));

			if (extension.isForge()) {
				dependencyManager.addProvider(new ForgeProvider(project));
				dependencyManager.addProvider(new ForgeUserdevProvider(project));
			}

			if (extension.shouldGenerateSrgTiny()) {
				dependencyManager.addProvider(new SrgProvider(project));
			}

			if (extension.isForge()) {
				dependencyManager.addProvider(new McpConfigProvider(project));
				dependencyManager.addProvider(new PatchProvider(project));
				dependencyManager.addProvider(new ForgeUniversalProvider(project));
			}

			dependencyManager.addProvider(extension.isForge() ? new FieldMigratedMappingsProvider(project) : new MappingsProvider(project));
			dependencyManager.addProvider(new LaunchProvider(project));

			dependencyManager.handleDependencies(project);

			project.getTasks().getByName("idea").finalizedBy(project.getTasks().getByName("genIdeaWorkspace"));
			project.getTasks().getByName("eclipse").finalizedBy(project.getTasks().getByName("genEclipseRuns"));
			project.getTasks().getByName("cleanEclipse").finalizedBy(project.getTasks().getByName("cleanEclipseRuns"));

			SetupIntelijRunConfigs.setup(project);
			GenVsCodeProjectTask.generate(project);

			// Enables the default mod remapper
			if (extension.remapMod) {
				RemapConfiguration.setupDefaultRemap(project);
			} else {
				AbstractArchiveTask jarTask = (AbstractArchiveTask) project.getTasks().getByName("jar");
				extension.getUnmappedModCollection().from(jarTask);
			}

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
		});

		if (p.getPluginManager().hasPlugin("org.jetbrains.kotlin.kapt")) {
			// If loom is applied after kapt, then kapt will use the AP arguments too early for loom to pass the arguments we need for mixin.
			throw new IllegalArgumentException("fabric-loom must be applied BEFORE kapt in the plugins { } block.");
		}
	}

	private static void extendsFrom(String a, String b, Project project) {
		project.getConfigurations().getByName(a).extendsFrom(project.getConfigurations().getByName(b));
	}
}
