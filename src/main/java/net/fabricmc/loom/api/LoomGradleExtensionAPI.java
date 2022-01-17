/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021 FabricMC
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

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.publish.maven.MavenPublication;
import org.jetbrains.annotations.ApiStatus;

import net.fabricmc.loom.api.decompilers.DecompilerOptions;
import net.fabricmc.loom.api.mappings.layered.spec.LayeredMappingSpecBuilder;
import net.fabricmc.loom.configuration.ide.RunConfigSettings;
import net.fabricmc.loom.configuration.processors.JarProcessor;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftJarConfiguration;
import net.fabricmc.loom.util.DeprecationHelper;

/**
 * This is the public api available exposed to build scripts.
 */
public interface LoomGradleExtensionAPI {
	@ApiStatus.Internal
	DeprecationHelper getDeprecationHelper();

	RegularFileProperty getAccessWidenerPath();

	Property<Boolean> getShareRemapCaches();

	default void shareCaches() {
		getShareRemapCaches().set(true);
	}

	NamedDomainObjectContainer<DecompilerOptions> getDecompilerOptions();

	void decompilers(Action<NamedDomainObjectContainer<DecompilerOptions>> action);

	ListProperty<JarProcessor> getGameJarProcessors();

	default void addJarProcessor(JarProcessor processor) {
		getGameJarProcessors().add(processor);
	}

	ConfigurableFileCollection getLog4jConfigs();

	default Dependency officialMojangMappings() {
		return layered(LayeredMappingSpecBuilder::officialMojangMappings);
	}

	Dependency layered(Action<LayeredMappingSpecBuilder> action);

	Property<Boolean> getRemapArchives();

	void runs(Action<NamedDomainObjectContainer<RunConfigSettings>> action);

	NamedDomainObjectContainer<RunConfigSettings> getRunConfigs();

	void mixin(Action<MixinExtensionAPI> action);

	@ApiStatus.Experimental
	// TODO: move this from LoomGradleExtensionAPI to LoomGradleExtension once getRefmapName & setRefmapName is removed.
	MixinExtensionAPI getMixin();

	Property<String> getCustomMinecraftManifest();

	/**
	 * If true, Loom will replace the {@code -dev} jars in the {@code *Elements} configurations
	 * with remapped outgoing variants.
	 *
	 * <p>Will only apply if {@link #getRemapArchives()} is also true.
	 *
	 * @return the property controlling the setup of remapped variants
	 */
	Property<Boolean> getSetupRemappedVariants();

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
	 * Use "%1$s" as a placeholder for the minecraft version.
	 *
	 * @return the intermediary url template
	 */
	Property<String> getIntermediaryUrl();

	/**
	 * When true loom will inject interfaces declared in mod manifests into the minecraft jar file.
	 * This is used to expose interfaces that are implemented on Minecraft classes by mixins at runtime
	 * in the dev environment.
	 *
	 * @return the property controlling interface injection.
	 */
	Property<Boolean> getEnableInterfaceInjection();

	@ApiStatus.Experimental
	Property<MinecraftJarConfiguration> getMinecraftJarConfiguration();

	@ApiStatus.Experimental
	default void serverOnlyMinecraftJar() {
		getMinecraftJarConfiguration().set(MinecraftJarConfiguration.SERVER_ONLY);
	}

	@ApiStatus.Experimental
	default void splitMinecraftJar() {
		getMinecraftJarConfiguration().set(MinecraftJarConfiguration.SPLIT);
	}
}
