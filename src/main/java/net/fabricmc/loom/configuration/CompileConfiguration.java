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

import static net.fabricmc.loom.util.Constants.Configurations;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.function.Consumer;

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.AbstractCopyTask;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.api.tasks.testing.Test;
import org.jspecify.annotations.Nullable;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.InterfaceInjectionExtensionAPI;
import net.fabricmc.loom.build.mixin.GroovyApInvoker;
import net.fabricmc.loom.build.mixin.JavaApInvoker;
import net.fabricmc.loom.build.mixin.KaptApInvoker;
import net.fabricmc.loom.build.mixin.ScalaApInvoker;
import net.fabricmc.loom.configuration.accesswidener.AccessWidenerJarProcessor;
import net.fabricmc.loom.configuration.ifaceinject.InterfaceInjectionProcessor;
import net.fabricmc.loom.configuration.processors.JsrAnnotationRemapperProcessor;
import net.fabricmc.loom.configuration.processors.MinecraftJarProcessorManager;
import net.fabricmc.loom.configuration.processors.ModJavadocProcessor;
import net.fabricmc.loom.configuration.processors.speccontext.DebofConfiguration;
import net.fabricmc.loom.configuration.providers.mappings.LayeredMappingsFactory;
import net.fabricmc.loom.configuration.providers.mappings.MappingConfiguration;
import net.fabricmc.loom.configuration.providers.mappings.NoRemapMappingConfiguration;
import net.fabricmc.loom.configuration.providers.mappings.RemapMappingConfiguration;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftCacheLocks;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftMetadataProvider;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftProvider;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftSourceSets;
import net.fabricmc.loom.configuration.providers.minecraft.mapped.AbstractMappedMinecraftProvider;
import net.fabricmc.loom.configuration.providers.minecraft.mapped.IntermediaryMinecraftProvider;
import net.fabricmc.loom.configuration.providers.minecraft.mapped.NamedMinecraftProvider;
import net.fabricmc.loom.extension.MixinExtension;
import net.fabricmc.loom.task.service.ClasspathGroupService;
import net.fabricmc.loom.util.Checksum;
import net.fabricmc.loom.util.ExceptionUtil;
import net.fabricmc.loom.util.ProcessLock;
import net.fabricmc.loom.util.ProcessLock.LockResult;
import net.fabricmc.loom.util.gradle.GradleUtils;
import net.fabricmc.loom.util.gradle.daemon.DaemonUtils;
import net.fabricmc.loom.util.service.ScopedServiceFactory;
import net.fabricmc.loom.util.service.ServiceFactory;

public abstract class CompileConfiguration implements Runnable {
	@Inject
	protected abstract Project getProject();

	@Inject
	protected abstract TaskContainer getTasks();

	@Override
	public void run() {
		LoomGradleExtension extension = LoomGradleExtension.get(getProject());

		getTasks().named(JavaPlugin.JAVADOC_TASK_NAME, Javadoc.class).configure(javadoc -> {
			final SourceSet minecraftSourceSet = extension.getMinecraftSourceSet();
			javadoc.setClasspath(minecraftSourceSet.getOutput().plus(minecraftSourceSet.getCompileClasspath()));
		});

		afterEvaluationWithService((serviceFactory) -> {
			final ConfigContext configContext = new ConfigContextImpl(getProject(), serviceFactory, extension);

			if (extension.disableObfuscation()) {
				DebofConfiguration.create(getProject());
			}

			MinecraftSourceSets.get(getProject()).afterEvaluate(getProject());

			final boolean previousRefreshDeps = extension.refreshDeps();

			final ProcessLock lock = createLock();
			final LockResult lockResult = lock.acquire();

			if (lockResult != LockResult.ACQUIRED_CLEAN) {
				getProject().getLogger().lifecycle("Found existing cache lock file ({}), rebuilding loom cache. This may have been caused by a failed or canceled build.", lockResult);
				extension.setRefreshDeps(true);
			}

			try {
				final MinecraftMetadataProvider metadataProvider = MinecraftMetadataProvider.create(configContext);
				extension.setMetadataProvider(metadataProvider);
				final MinecraftProvider minecraftProvider;
				final MappingConfigurationSetup mappingConfigurationSetup;

				// Version manifests are shared across Minecraft caches.
				synchronized (MinecraftCacheLocks.getGlobalLock()) {
					metadataProvider.getVersionMeta();
					minecraftProvider = createMinecraftProvider(configContext, metadataProvider);
				}

				final Object cacheLock = MinecraftCacheLocks.getCacheLock(minecraftProvider.workingDir().toPath());

				synchronized (cacheLock) {
					minecraftProvider.provide();
				}

				minecraftProvider.provideLibraries();

				synchronized (cacheLock) {
					mappingConfigurationSetup = provideMappings(configContext, minecraftProvider);
				}

				if (mappingConfigurationSetup != null) {
					extension.setMappingConfiguration(mappingConfigurationSetup.mappingConfiguration());
					mappingConfigurationSetup.mappingConfiguration().applyToProject(getProject(), mappingConfigurationSetup.dependencyInfo());
				}

				createMappedMinecraftProviders(configContext);
				// Processor discovery can configure dependency projects, so it must run outside the cache lock.
				registerGameProcessors(configContext);
				final MinecraftJarProcessorManager minecraftJarProcessorManager = MinecraftJarProcessorManager.create(getProject());
				createProcessedNamedMinecraftProvider(configContext, minecraftJarProcessorManager);

				provideMappedMinecraft(configContext, cacheLock);

				var dependencyManager = new LoomDependencyManager(getProject(), serviceFactory, extension);
				dependencyManager.handleDependencies();
			} catch (Exception e) {
				ExceptionUtil.processException(e, DaemonUtils.Context.fromProject(getProject()));
				createLock().disown();
				throw ExceptionUtil.createDescriptiveWrapper(RuntimeException::new, "Failed to setup Minecraft", e);
			}

			createLock().release();
			extension.setRefreshDeps(previousRefreshDeps);

			MixinExtension mixin = LoomGradleExtension.get(getProject()).getMixin();

			if (mixin.getUseLegacyMixinAp().get()) {
				setupMixinAp(mixin);
			}

			configureDecompileTasks(configContext);
			configureTestTask();
		});

		finalizedBy("eclipse", "genEclipseRuns");

		if (!extension.disableObfuscation()) {
			// Add the "dev" jar to the "namedElements" configuration
			getProject().artifacts(artifactHandler -> artifactHandler.add(Configurations.NAMED_ELEMENTS, getTasks().named("jar")));
		}

		// Ensure that the encoding is set to UTF-8, no matter what the system default is
		// this fixes some edge cases with special characters not displaying correctly
		// see http://yodaconditions.net/blog/fix-for-java-file-encoding-problems-with-gradle.html
		getTasks().withType(AbstractCopyTask.class).configureEach(abstractCopyTask -> abstractCopyTask.setFilteringCharset(StandardCharsets.UTF_8.name()));
		getTasks().withType(JavaCompile.class).configureEach(javaCompile -> javaCompile.getOptions().setEncoding(StandardCharsets.UTF_8.name()));

		if (getProject().getPluginManager().hasPlugin("org.jetbrains.kotlin.kapt")) {
			// If loom is applied after kapt, then kapt will use the AP arguments too early for loom to pass the arguments we need for mixin.
			throw new IllegalArgumentException("fabric-loom must be applied BEFORE kapt in the plugins { } block.");
		}
	}

	private MinecraftProvider createMinecraftProvider(ConfigContext configContext, MinecraftMetadataProvider metadataProvider) {
		final LoomGradleExtension extension = configContext.extension();
		final MinecraftProvider minecraftProvider = extension.getMinecraftJarConfiguration().get().createMinecraftProvider(metadataProvider, configContext);
		extension.setMinecraftProvider(minecraftProvider);
		return minecraftProvider;
	}

	@Nullable
	private MappingConfigurationSetup provideMappings(ConfigContext configContext, MinecraftProvider minecraftProvider) throws Exception {
		final Project project = configContext.project();
		final LoomGradleExtension extension = configContext.extension();

		if (!extension.disableObfuscation()) {
			// Realise the dependencies without actually resolving them, this forces any lazy providers to be created, populating the layered mapping factories.
			project.getConfigurations().getByName(Configurations.MAPPINGS).getDependencies().toArray();

			// Created any layered mapping files.
			LayeredMappingsFactory.afterEvaluate(configContext);

			// Resolve the mapping files from the configuration
			final DependencyInfo mappingsDep = DependencyInfo.create(getProject(), Configurations.MAPPINGS);
			final MappingConfiguration mappingConfiguration = RemapMappingConfiguration.create(getProject(), configContext.serviceFactory(), mappingsDep, minecraftProvider);
			return new MappingConfigurationSetup(mappingConfiguration, mappingsDep);
		} else {
			var annotations = project.getConfigurations().getByName(Configurations.ANNOTATIONS);

			if (!annotations.getDependencies().isEmpty()) {
				final DependencyInfo annotationsDep = DependencyInfo.create(getProject(), annotations);
				final MappingConfiguration mappingConfiguration = NoRemapMappingConfiguration.create(getProject(), annotationsDep, minecraftProvider);
				return new MappingConfigurationSetup(mappingConfiguration, annotationsDep);
			}
		}

		return null;
	}

	private record MappingConfigurationSetup(MappingConfiguration mappingConfiguration, DependencyInfo dependencyInfo) {
	}

	private void createMappedMinecraftProviders(ConfigContext configContext) {
		final Project project = configContext.project();
		final LoomGradleExtension extension = configContext.extension();
		final var jarConfiguration = extension.getMinecraftJarConfiguration().get();
		IntermediaryMinecraftProvider<?> intermediaryMinecraftProvider = extension.disableObfuscation() ? null : jarConfiguration.createIntermediaryMinecraftProvider(project);
		NamedMinecraftProvider<?> namedMinecraftProvider = jarConfiguration.createNamedMinecraftProvider(project);

		if (intermediaryMinecraftProvider != null) {
			extension.setIntermediaryMinecraftProvider(intermediaryMinecraftProvider);
		}

		extension.setNamedMinecraftProvider(namedMinecraftProvider);
	}

	private void createProcessedNamedMinecraftProvider(ConfigContext configContext, @Nullable MinecraftJarProcessorManager minecraftJarProcessorManager) {
		if (minecraftJarProcessorManager == null) {
			return;
		}

		final LoomGradleExtension extension = configContext.extension();
		final var jarConfiguration = extension.getMinecraftJarConfiguration().get();
		final NamedMinecraftProvider<?> namedMinecraftProvider = jarConfiguration.createProcessedNamedMinecraftProvider(extension.getNamedMinecraftProvider(), minecraftJarProcessorManager);
		extension.setNamedMinecraftProvider(namedMinecraftProvider);
	}

	private void provideMappedMinecraft(ConfigContext configContext, Object cacheLock) throws Exception {
		final LoomGradleExtension extension = configContext.extension();
		final IntermediaryMinecraftProvider<?> intermediaryMinecraftProvider = extension.disableObfuscation() ? null : extension.getIntermediaryMinecraftProvider();
		final NamedMinecraftProvider<?> namedMinecraftProvider = extension.getNamedMinecraftProvider();

		final var provideContext = new AbstractMappedMinecraftProvider.ProvideContext(false, extension.refreshDeps(), configContext);

		synchronized (cacheLock) {
			if (intermediaryMinecraftProvider != null) {
				intermediaryMinecraftProvider.provide(provideContext);
			}

			namedMinecraftProvider.provide(provideContext);
		}

		if (intermediaryMinecraftProvider != null) {
			intermediaryMinecraftProvider.applyDependencies();
		}

		namedMinecraftProvider.applyDependencies();
	}

	private void registerGameProcessors(ConfigContext configContext) {
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

		if (!extension.getRemapJsrAnnotationsToJetBrains().get()) {
			extension.addMinecraftJarProcessor(JsrAnnotationRemapperProcessor.class, "fabric-loom:jsr-annotations");
		}
	}

	private void setupMixinAp(MixinExtension mixin) {
		mixin.init();

		// Disable some things used by log4j via the mixin AP that prevent it from being garbage collected
		System.setProperty("log4j2.disable.jmx", "true");
		System.setProperty("log4j.shutdownHookEnabled", "false");
		System.setProperty("log4j.skipJansi", "true");

		getProject().getLogger().info("Configuring compiler arguments for Java");

		new JavaApInvoker(getProject()).configureMixin();

		if (getProject().getPluginManager().hasPlugin("scala")) {
			getProject().getLogger().info("Configuring compiler arguments for Scala");
			new ScalaApInvoker(getProject()).configureMixin();
		}

		if (getProject().getPluginManager().hasPlugin("org.jetbrains.kotlin.kapt")) {
			getProject().getLogger().info("Configuring compiler arguments for Kapt plugin");
			new KaptApInvoker(getProject()).configureMixin();
		}

		if (getProject().getPluginManager().hasPlugin("groovy")) {
			getProject().getLogger().info("Configuring compiler arguments for Groovy");
			new GroovyApInvoker(getProject()).configureMixin();
		}
	}

	private void configureDecompileTasks(ConfigContext configContext) {
		final LoomGradleExtension extension = configContext.extension();

		extension.getMinecraftJarConfiguration().get()
				.createDecompileConfiguration(getProject())
				.afterEvaluation();
	}

	private void configureTestTask() {
		final LoomGradleExtension extension = LoomGradleExtension.get(getProject());

		if (extension.getMods().isEmpty()) {
			return;
		}

		getProject().getTasks().named(JavaPlugin.TEST_TASK_NAME, Test.class, test -> {
			Provider<ClasspathGroupService.Options> optionsProvider = ClasspathGroupService.create(getProject());
			test.getInputs().property("LoomClassPathGroups", optionsProvider);
			test.getInputs().files(optionsProvider.map((ClasspathGroupService.Options::getExternalClasspathGroups)));

			test.doFirst(new Action<Task>() {
				@Override
				public void execute(Task task) {
					try (ScopedServiceFactory serviceFactory = new ScopedServiceFactory()) {
						var options = (ClasspathGroupService.Options) task.getInputs().getProperties().get("LoomClassPathGroups");
						ClasspathGroupService classpathGroupService = serviceFactory.get(options);

						if (classpathGroupService.hasGroups()) {
							test.systemProperty("fabric.classPathGroups", classpathGroupService.getClasspathGroupsPropertyValue());
						}
					} catch (IOException e) {
						throw new UncheckedIOException("Failed to get classpath groups", e);
					}
				}
			});
		});
	}

	private ProcessLock createLock() {
		final LoomGradleExtension extension = LoomGradleExtension.get(getProject());
		final Path cacheDirectory = extension.getFiles().getUserCache().toPath();
		final String pathHash = Checksum.of(getProject()).sha1().hex();
		return new ProcessLock(
				cacheDirectory.resolve("." + pathHash + ".lock"),
				"Lock for cache='%s', project='%s'".formatted(
						cacheDirectory, getProject().absoluteProjectPath(getProject().getPath())
				),
				getProject()
		);
	}

	private void finalizedBy(String a, String b) {
		getTasks().named(a).configure(task -> task.finalizedBy(getTasks().named(b)));
	}

	private void afterEvaluationWithService(Consumer<ServiceFactory> consumer) {
		GradleUtils.afterSuccessfulEvaluation(getProject(), () -> {
			try (var serviceFactory = new ScopedServiceFactory()) {
				consumer.accept(serviceFactory);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		});
	}
}
