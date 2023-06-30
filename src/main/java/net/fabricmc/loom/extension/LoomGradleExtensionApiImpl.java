/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021-2022 FabricMC
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

package net.fabricmc.loom.extension;

import java.io.File;

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.NamedDomainObjectList;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.tasks.SourceSet;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.InterfaceInjectionExtensionAPI;
import net.fabricmc.loom.api.LoomGradleExtensionAPI;
import net.fabricmc.loom.api.MixinExtensionAPI;
import net.fabricmc.loom.api.ModSettings;
import net.fabricmc.loom.api.RemapConfigurationSettings;
import net.fabricmc.loom.api.decompilers.DecompilerOptions;
import net.fabricmc.loom.api.mappings.intermediate.IntermediateMappingsProvider;
import net.fabricmc.loom.api.mappings.layered.spec.LayeredMappingSpecBuilder;
import net.fabricmc.loom.api.processor.MinecraftJarProcessor;
import net.fabricmc.loom.configuration.RemapConfigurations;
import net.fabricmc.loom.configuration.ide.RunConfigSettings;
import net.fabricmc.loom.configuration.processors.JarProcessor;
import net.fabricmc.loom.configuration.providers.mappings.GradleMappingContext;
import net.fabricmc.loom.configuration.providers.mappings.LayeredMappingSpec;
import net.fabricmc.loom.configuration.providers.mappings.LayeredMappingSpecBuilderImpl;
import net.fabricmc.loom.configuration.providers.mappings.LayeredMappingsDependency;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftJarConfiguration;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftSourceSets;
import net.fabricmc.loom.task.GenerateSourcesTask;
import net.fabricmc.loom.util.DeprecationHelper;
import net.fabricmc.loom.util.gradle.SourceSetHelper;

/**
 * This class implements the public extension api.
 */
public abstract class LoomGradleExtensionApiImpl implements LoomGradleExtensionAPI {
	protected final DeprecationHelper deprecationHelper;
	@Deprecated()
	protected final ListProperty<JarProcessor> jarProcessors;
	protected final ConfigurableFileCollection log4jConfigs;
	protected final RegularFileProperty accessWidener;
	protected final Property<String> customManifest;
	protected final Property<Boolean> transitiveAccessWideners;
	protected final Property<Boolean> modProvidedJavadoc;
	protected final Property<String> intermediary;
	protected final Property<IntermediateMappingsProvider> intermediateMappingsProvider;
	private final Property<Boolean> runtimeOnlyLog4j;
	private final Property<Boolean> splitModDependencies;
	private final Property<MinecraftJarConfiguration> minecraftJarConfiguration;
	private final Property<Boolean> splitEnvironmentalSourceSet;
	private final InterfaceInjectionExtensionAPI interfaceInjectionExtension;

	private final ModVersionParser versionParser;

	private final NamedDomainObjectContainer<RunConfigSettings> runConfigs;
	private final NamedDomainObjectContainer<DecompilerOptions> decompilers;
	private final NamedDomainObjectContainer<ModSettings> mods;
	private final NamedDomainObjectList<RemapConfigurationSettings> remapConfigurations;
	private final ListProperty<MinecraftJarProcessor<?>> minecraftJarProcessors;

	// A common mistake with layered mappings is to call the wrong `officialMojangMappings` method, use this to keep track of when we are building a layered mapping spec.
	protected final ThreadLocal<Boolean> layeredSpecBuilderScope = ThreadLocal.withInitial(() -> false);

	protected LoomGradleExtensionApiImpl(Project project, LoomFiles directories) {
		this.jarProcessors = project.getObjects().listProperty(JarProcessor.class)
				.empty();
		this.log4jConfigs = project.files(directories.getDefaultLog4jConfigFile());
		this.accessWidener = project.getObjects().fileProperty();
		this.customManifest = project.getObjects().property(String.class);
		this.transitiveAccessWideners = project.getObjects().property(Boolean.class)
				.convention(true);
		this.transitiveAccessWideners.finalizeValueOnRead();
		this.modProvidedJavadoc = project.getObjects().property(Boolean.class)
				.convention(true);
		this.modProvidedJavadoc.finalizeValueOnRead();
		this.intermediary = project.getObjects().property(String.class)
				.convention("https://maven.fabricmc.net/net/fabricmc/intermediary/%1$s/intermediary-%1$s-v2.jar");

		this.intermediateMappingsProvider = project.getObjects().property(IntermediateMappingsProvider.class);
		this.intermediateMappingsProvider.finalizeValueOnRead();

		this.versionParser = new ModVersionParser(project);

		this.deprecationHelper = new DeprecationHelper.ProjectBased(project);

		this.runConfigs = project.container(RunConfigSettings.class,
				baseName -> project.getObjects().newInstance(RunConfigSettings.class, project, baseName));
		this.decompilers = project.getObjects().domainObjectContainer(DecompilerOptions.class);
		this.mods = project.getObjects().domainObjectContainer(ModSettings.class);
		this.remapConfigurations = project.getObjects().namedDomainObjectList(RemapConfigurationSettings.class);
		//noinspection unchecked
		this.minecraftJarProcessors = (ListProperty<MinecraftJarProcessor<?>>) (Object) project.getObjects().listProperty(MinecraftJarProcessor.class);
		this.minecraftJarProcessors.finalizeValueOnRead();

		this.minecraftJarConfiguration = project.getObjects().property(MinecraftJarConfiguration.class).convention(MinecraftJarConfiguration.MERGED);
		this.minecraftJarConfiguration.finalizeValueOnRead();

		this.accessWidener.finalizeValueOnRead();
		this.getGameJarProcessors().finalizeValueOnRead();

		this.runtimeOnlyLog4j = project.getObjects().property(Boolean.class).convention(false);
		this.runtimeOnlyLog4j.finalizeValueOnRead();

		this.splitModDependencies = project.getObjects().property(Boolean.class).convention(true);
		this.splitModDependencies.finalizeValueOnRead();

		this.interfaceInjectionExtension = project.getObjects().newInstance(InterfaceInjectionExtensionAPI.class);
		this.interfaceInjectionExtension.getIsEnabled().convention(true);

		this.splitEnvironmentalSourceSet = project.getObjects().property(Boolean.class).convention(false);
		this.splitEnvironmentalSourceSet.finalizeValueOnRead();

		// Enable dep iface injection by default
		interfaceInjection(interfaceInjection -> {
			interfaceInjection.getEnableDependencyInterfaceInjection().convention(true).finalizeValueOnRead();
		});
	}

	@Override
	public DeprecationHelper getDeprecationHelper() {
		return deprecationHelper;
	}

	@Override
	public RegularFileProperty getAccessWidenerPath() {
		return accessWidener;
	}

	@Override
	public NamedDomainObjectContainer<DecompilerOptions> getDecompilerOptions() {
		return decompilers;
	}

	@Override
	public void decompilers(Action<NamedDomainObjectContainer<DecompilerOptions>> action) {
		action.execute(decompilers);
	}

	@Override
	public ListProperty<JarProcessor> getGameJarProcessors() {
		return jarProcessors;
	}

	@Override
	public ListProperty<MinecraftJarProcessor<?>> getMinecraftJarProcessors() {
		return minecraftJarProcessors;
	}

	@Override
	public void addMinecraftJarProcessor(Class<? extends MinecraftJarProcessor<?>> clazz, Object... parameters) {
		getMinecraftJarProcessors().add(getProject().getObjects().newInstance(clazz, parameters));
	}

	@Override
	public Dependency officialMojangMappings() {
		if (layeredSpecBuilderScope.get()) {
			throw new IllegalStateException("Use `officialMojangMappings()` when configuring layered mappings, not the extension method `loom.officialMojangMappings()`");
		}

		return layered(LayeredMappingSpecBuilder::officialMojangMappings);
	}

	@Override
	public Dependency layered(Action<LayeredMappingSpecBuilder> action) {
		LayeredMappingSpecBuilderImpl builder = new LayeredMappingSpecBuilderImpl();

		layeredSpecBuilderScope.set(true);
		action.execute(builder);
		layeredSpecBuilderScope.set(false);

		LayeredMappingSpec builtSpec = builder.build();
		return new LayeredMappingsDependency(getProject(), new GradleMappingContext(getProject(), builtSpec.getVersion().replace("+", "_").replace(".", "_")), builtSpec, builtSpec.getVersion());
	}

	@Override
	public void runs(Action<NamedDomainObjectContainer<RunConfigSettings>> action) {
		action.execute(runConfigs);
	}

	@Override
	public NamedDomainObjectContainer<RunConfigSettings> getRunConfigs() {
		return runConfigs;
	}

	@Override
	public ConfigurableFileCollection getLog4jConfigs() {
		return log4jConfigs;
	}

	@Override
	public void mixin(Action<MixinExtensionAPI> action) {
		action.execute(getMixin());
	}

	@Override
	public Property<String> getCustomMinecraftManifest() {
		return customManifest;
	}

	@Override
	public String getModVersion() {
		return versionParser.getModVersion();
	}

	@Override
	public Property<Boolean> getEnableTransitiveAccessWideners() {
		return transitiveAccessWideners;
	}

	@Override
	public Property<Boolean> getEnableModProvidedJavadoc() {
		return modProvidedJavadoc;
	}

	protected abstract Project getProject();

	protected abstract LoomFiles getFiles();

	@Override
	public Property<String> getIntermediaryUrl() {
		return intermediary;
	}

	@Override
	public IntermediateMappingsProvider getIntermediateMappingsProvider() {
		return intermediateMappingsProvider.get();
	}

	@Override
	public void setIntermediateMappingsProvider(IntermediateMappingsProvider intermediateMappingsProvider) {
		this.intermediateMappingsProvider.set(intermediateMappingsProvider);
	}

	@Override
	public <T extends IntermediateMappingsProvider> void setIntermediateMappingsProvider(Class<T> clazz, Action<T> action) {
		T provider = getProject().getObjects().newInstance(clazz);
		configureIntermediateMappingsProviderInternal(provider);
		action.execute(provider);
		intermediateMappingsProvider.set(provider);
	}

	@Override
	public File getMappingsFile() {
		return LoomGradleExtension.get(getProject()).getMappingConfiguration().tinyMappings.toFile();
	}

	@Override
	public GenerateSourcesTask getDecompileTask(DecompilerOptions options, boolean client) {
		final String decompilerName = options.getFormattedName();
		final String taskName;

		if (areEnvironmentSourceSetsSplit()) {
			taskName = "gen%sSourcesWith%s".formatted(client ? "ClientOnly" : "Common", decompilerName);
		} else {
			taskName = "genSourcesWith" + decompilerName;
		}

		return (GenerateSourcesTask) getProject().getTasks().getByName(taskName);
	}

	protected abstract <T extends IntermediateMappingsProvider> void configureIntermediateMappingsProviderInternal(T provider);

	@Override
	public void disableDeprecatedPomGeneration(MavenPublication publication) {
		net.fabricmc.loom.configuration.MavenPublication.excludePublication(publication);
	}

	@Override
	public Property<MinecraftJarConfiguration> getMinecraftJarConfiguration() {
		return minecraftJarConfiguration;
	}

	@Override
	public Property<Boolean> getRuntimeOnlyLog4j() {
		return runtimeOnlyLog4j;
	}

	@Override
	public Property<Boolean> getSplitModDependencies() {
		return splitModDependencies;
	}

	@Override
	public void splitEnvironmentSourceSets() {
		splitMinecraftJar();

		splitEnvironmentalSourceSet.set(true);

		// We need to lock these values, as we setup the new source sets right away.
		splitEnvironmentalSourceSet.finalizeValue();
		minecraftJarConfiguration.finalizeValue();

		MinecraftSourceSets.get(getProject()).evaluateSplit(getProject());
	}

	@Override
	public boolean areEnvironmentSourceSetsSplit() {
		return splitEnvironmentalSourceSet.get();
	}

	@Override
	public InterfaceInjectionExtensionAPI getInterfaceInjection() {
		return interfaceInjectionExtension;
	}

	@Override
	public void mods(Action<NamedDomainObjectContainer<ModSettings>> action) {
		action.execute(getMods());
	}

	@Override
	public NamedDomainObjectContainer<ModSettings> getMods() {
		return mods;
	}

	@Override
	public NamedDomainObjectList<RemapConfigurationSettings> getRemapConfigurations() {
		return remapConfigurations;
	}

	@Override
	public RemapConfigurationSettings addRemapConfiguration(String name, Action<RemapConfigurationSettings> action) {
		final RemapConfigurationSettings configurationSettings = getProject().getObjects().newInstance(RemapConfigurationSettings.class, name);

		// TODO remove in 2.0, this is a fallback to mimic the previous (Broken) behaviour
		configurationSettings.getSourceSet().convention(SourceSetHelper.getMainSourceSet(getProject()));

		action.execute(configurationSettings);
		RemapConfigurations.applyToProject(getProject(), configurationSettings);
		remapConfigurations.add(configurationSettings);

		return configurationSettings;
	}

	@Override
	public void createRemapConfigurations(SourceSet sourceSet) {
		RemapConfigurations.setupForSourceSet(getProject(), sourceSet);
	}

	// This is here to ensure that LoomGradleExtensionApiImpl compiles without any unimplemented methods
	private final class EnsureCompile extends LoomGradleExtensionApiImpl {
		private EnsureCompile() {
			super(null, null);
			throw new RuntimeException();
		}

		@Override
		public DeprecationHelper getDeprecationHelper() {
			throw new RuntimeException("Yeah... something is really wrong");
		}

		@Override
		protected Project getProject() {
			throw new RuntimeException("Yeah... something is really wrong");
		}

		@Override
		protected LoomFiles getFiles() {
			throw new RuntimeException("Yeah... something is really wrong");
		}

		@Override
		protected <T extends IntermediateMappingsProvider> void configureIntermediateMappingsProviderInternal(T provider) {
			throw new RuntimeException("Yeah... something is really wrong");
		}

		@Override
		public MixinExtension getMixin() {
			throw new RuntimeException("Yeah... something is really wrong");
		}
	}
}
