/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021-2023 FabricMC
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

package net.fabricmc.loom.api;

import java.io.File;
import java.util.List;

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.NamedDomainObjectList;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.tasks.SourceSet;
import org.jetbrains.annotations.ApiStatus;

import net.fabricmc.loom.api.decompilers.DecompilerOptions;
import net.fabricmc.loom.api.manifest.VersionsManifestsAPI;
import net.fabricmc.loom.api.mappings.intermediate.IntermediateMappingsProvider;
import net.fabricmc.loom.api.mappings.layered.spec.LayeredMappingSpecBuilder;
import net.fabricmc.loom.api.processor.MinecraftJarProcessor;
import net.fabricmc.loom.api.remapping.RemapperExtension;
import net.fabricmc.loom.api.remapping.RemapperParameters;
import net.fabricmc.loom.configuration.ide.RunConfigSettings;
import net.fabricmc.loom.configuration.processors.JarProcessor;
import net.fabricmc.loom.configuration.providers.mappings.NoOpIntermediateMappingsProvider;
import net.fabricmc.loom.configuration.providers.minecraft.ManifestLocations;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftJarConfiguration;
import net.fabricmc.loom.task.GenerateSourcesTask;
import net.fabricmc.loom.util.DeprecationHelper;

/**
 * This is the public api available exposed to build scripts.
 */
public interface LoomGradleExtensionAPI {
	@ApiStatus.Internal
	DeprecationHelper getDeprecationHelper();

	RegularFileProperty getAccessWidenerPath();

	NamedDomainObjectContainer<DecompilerOptions> getDecompilerOptions();

	void decompilers(Action<NamedDomainObjectContainer<DecompilerOptions>> action);

	@Deprecated()
	ListProperty<JarProcessor> getGameJarProcessors();

	@Deprecated()
	default void addJarProcessor(JarProcessor processor) {
		getGameJarProcessors().add(processor);
	}

	ListProperty<MinecraftJarProcessor<?>> getMinecraftJarProcessors();

	void addMinecraftJarProcessor(Class<? extends MinecraftJarProcessor<?>> clazz, Object... parameters);

	ConfigurableFileCollection getLog4jConfigs();

	Dependency officialMojangMappings();

	Dependency layered(Action<LayeredMappingSpecBuilder> action);

	void runs(Action<NamedDomainObjectContainer<RunConfigSettings>> action);

	NamedDomainObjectContainer<RunConfigSettings> getRunConfigs();

	/**
	 * {@return the value of {@link #getRunConfigs}}
	 * This is an alias for it that matches {@link #runs}.
	 */
	default NamedDomainObjectContainer<RunConfigSettings> getRuns() {
		return getRunConfigs();
	}

	void mixin(Action<MixinExtensionAPI> action);

	/**
	 * Optionally register and configure a {@link ModSettings} object. The name should match the modid.
	 * This is generally only required when the mod spans across multiple classpath directories, such as when using split sourcesets.
	 */
	void mods(Action<NamedDomainObjectContainer<ModSettings>> action);

	NamedDomainObjectContainer<ModSettings> getMods();

	NamedDomainObjectList<RemapConfigurationSettings> getRemapConfigurations();

	RemapConfigurationSettings addRemapConfiguration(String name, Action<RemapConfigurationSettings> action);

	void createRemapConfigurations(SourceSet sourceSet);

	default List<RemapConfigurationSettings> getCompileRemapConfigurations() {
		return getRemapConfigurations().stream().filter(element -> element.getOnCompileClasspath().get()).toList();
	}

	default List<RemapConfigurationSettings> getRuntimeRemapConfigurations() {
		return getRemapConfigurations().stream().filter(element -> element.getOnRuntimeClasspath().get()).toList();
	}

	@ApiStatus.Experimental
	// TODO: move this from LoomGradleExtensionAPI to LoomGradleExtension once getRefmapName & setRefmapName is removed.
	MixinExtensionAPI getMixin();

	default void interfaceInjection(Action<InterfaceInjectionExtensionAPI> action) {
		action.execute(getInterfaceInjection());
	}

	InterfaceInjectionExtensionAPI getInterfaceInjection();

	@ApiStatus.Experimental
	default void versionsManifests(Action<VersionsManifestsAPI> action) {
		action.execute(getVersionsManifests());
	}

	@ApiStatus.Experimental
	ManifestLocations getVersionsManifests();

	/**
	 * @deprecated use {@linkplain #getCustomMinecraftMetadata} instead
	 */
	@Deprecated
	default Property<String> getCustomMinecraftManifest() {
		return getCustomMinecraftMetadata();
	}

	Property<String> getCustomMinecraftMetadata();

	SetProperty<String> getKnownIndyBsms();

	/**
	 * Disables the deprecated POM generation for a publication.
	 * This is useful if you want to suppress deprecation warnings when you're not using software components.
	 *
	 * <p>Experimental API: Will be removed in Loom 0.12 together with the deprecated POM generation functionality.
	 *
	 * @param publication the maven publication
	 */
	@ApiStatus.Experimental
	void disableDeprecatedPomGeneration(MavenPublication publication);

	/**
	 * Reads the mod version from the fabric.mod.json file located in the main sourcesets resources.
	 * This is useful if you want to set the gradle version based of the version in the fabric.mod.json file.
	 *
	 * @return the version defined in the fabric.mod.json
	 */
	String getModVersion();

	/**
	 * When true loom will apply transitive access wideners from compile dependencies.
	 *
	 * @return the property controlling the transitive access wideners
	 */
	Property<Boolean> getEnableTransitiveAccessWideners();

	/**
	 * When true loom will apply mod provided javadoc from dependencies.
	 *
	 * @return the property controlling the mod provided javadoc
	 */
	Property<Boolean> getEnableModProvidedJavadoc();

	@ApiStatus.Experimental
	IntermediateMappingsProvider getIntermediateMappingsProvider();

	@ApiStatus.Experimental
	void setIntermediateMappingsProvider(IntermediateMappingsProvider intermediateMappingsProvider);

	@ApiStatus.Experimental
	<T extends IntermediateMappingsProvider> void setIntermediateMappingsProvider(Class<T> clazz, Action<T> action);

	/**
	 * An Experimental option to provide empty intermediate mappings, to be used for game versions without any intermediate mappings.
	 */
	@ApiStatus.Experimental
	default void noIntermediateMappings() {
		setIntermediateMappingsProvider(NoOpIntermediateMappingsProvider.class, p -> { });
	}

	/**
	 * Returns the tiny mappings file used to remap the game and mods.
	 */
	File getMappingsFile();

	/**
	 * Returns the {@link GenerateSourcesTask} for the given {@link DecompilerOptions}.
	 * When env source sets are split and the client param is true the decompile task for the client jar will be returned.
	 */
	GenerateSourcesTask getDecompileTask(DecompilerOptions options, boolean client);

	/**
	 * Use "%1$s" as a placeholder for the minecraft version.
	 *
	 * @return the intermediary url template
	 */
	Property<String> getIntermediaryUrl();

	@ApiStatus.Experimental
	Property<MinecraftJarConfiguration<?, ?, ?>> getMinecraftJarConfiguration();

	default void serverOnlyMinecraftJar() {
		getMinecraftJarConfiguration().set(MinecraftJarConfiguration.SERVER_ONLY);
	}

	default void clientOnlyMinecraftJar() {
		getMinecraftJarConfiguration().set(MinecraftJarConfiguration.CLIENT_ONLY);
	}

	default void splitMinecraftJar() {
		getMinecraftJarConfiguration().set(MinecraftJarConfiguration.SPLIT);
	}

	void splitEnvironmentSourceSets();

	boolean areEnvironmentSourceSetsSplit();

	Property<Boolean> getRuntimeOnlyLog4j();

	Property<Boolean> getSplitModDependencies();

	<T extends RemapperParameters> void addRemapperExtension(Class<? extends RemapperExtension<T>> remapperExtensionClass, Class<T> parametersClass, Action<T> parameterAction);

	/**
	 * @return The minecraft version, as a {@link Provider}.
	 */
	Provider<String> getMinecraftVersion();

	/**
	 * @return A lazily evaluated {@link FileCollection} containing the named minecraft jars.
	 */
	FileCollection getNamedMinecraftJars();
}
