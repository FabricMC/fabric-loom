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

import java.io.File;
import java.util.List;

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.jetbrains.annotations.ApiStatus;

import net.fabricmc.loom.api.decompilers.LoomDecompiler;
import net.fabricmc.loom.configuration.ide.RunConfigSettings;
import net.fabricmc.loom.configuration.processors.JarProcessor;
import net.fabricmc.loom.configuration.providers.mappings.LayeredMappingSpecBuilder;
import net.fabricmc.loom.util.DeprecationHelper;

/**
 * This is the public api available exposed to build scripts.
 */
public interface LoomGradleExtensionAPI {
	@ApiStatus.Internal
	DeprecationHelper getDeprecationHelper();

	RegularFileProperty getAccessWidenerPath();

	@Deprecated(forRemoval = true)
	@ApiStatus.ScheduledForRemoval(inVersion = "0.11")
	default File getAccessWidener() {
		getDeprecationHelper().replaceWithInLoom0_11("accessWidener", "accessWidenerPath");
		return getAccessWidenerPath().getAsFile().getOrNull();
	}

	@Deprecated(forRemoval = true)
	@ApiStatus.ScheduledForRemoval(inVersion = "0.11")
	void setAccessWidener(Object file);

	Property<Boolean> getShareRemapCaches();

	@Deprecated(forRemoval = true)
	@ApiStatus.ScheduledForRemoval(inVersion = "0.11")
	default void setShareCaches(boolean shareCaches) {
		getDeprecationHelper().replaceWithInLoom0_11("shareCaches", "shareRemapCaches");
		getShareRemapCaches().set(shareCaches);
	}

	@Deprecated(forRemoval = true)
	@ApiStatus.ScheduledForRemoval(inVersion = "0.11")
	default boolean isShareCaches() {
		getDeprecationHelper().replaceWithInLoom0_11("shareCaches", "shareRemapCaches");
		return getShareRemapCaches().get();
	}

	@Deprecated(forRemoval = true)
	@ApiStatus.ScheduledForRemoval(inVersion = "0.11")
	default void shareCaches() {
		setShareCaches(true);
	}

	ListProperty<LoomDecompiler> getLoomDecompilers();

	@Deprecated(forRemoval = true)
	@ApiStatus.ScheduledForRemoval(inVersion = "0.11")
	default List<LoomDecompiler> getDecompilers() {
		getDeprecationHelper().replaceWithInLoom0_11("decompilers", "loomDecompilers");
		return getLoomDecompilers().get();
	}

	@Deprecated(forRemoval = true)
	@ApiStatus.ScheduledForRemoval(inVersion = "0.11")
	default void addDecompiler(LoomDecompiler decompiler) {
		getDeprecationHelper().replaceWithInLoom0_11("decompilers", "loomDecompilers");
		getLoomDecompilers().add(decompiler);
	}

	ListProperty<JarProcessor> getMinecraftJarProcessors();

	@Deprecated(forRemoval = true)
	@ApiStatus.ScheduledForRemoval(inVersion = "0.11")
	default List<JarProcessor> getJarProcessors() {
		getDeprecationHelper().replaceWithInLoom0_11("jarProcessors", "minecraftJarProcessors");
		return getMinecraftJarProcessors().get();
	}

	@Deprecated(forRemoval = true)
	@ApiStatus.ScheduledForRemoval(inVersion = "0.11")
	default void addJarProcessor(JarProcessor processor) {
		getDeprecationHelper().replaceWithInLoom0_11("jarProcessors", "minecraftJarProcessors");
		getMinecraftJarProcessors().add(processor);
	}

	ConfigurableFileCollection getLog4jConfigs();

	default Dependency officialMojangMappings() {
		return layered(LayeredMappingSpecBuilder::officialMojangMappings);
	}

	Dependency layered(Action<LayeredMappingSpecBuilder> action);

	Property<String> getMixinRefmapName();

	@Deprecated(forRemoval = true)
	@ApiStatus.ScheduledForRemoval(inVersion = "0.11")
	default String getRefmapName() {
		getDeprecationHelper().replaceWithInLoom0_11("refmapName", "mixinRefmapName");
		return getMixinRefmapName().get();
	}

	@Deprecated(forRemoval = true)
	@ApiStatus.ScheduledForRemoval(inVersion = "0.11")
	default void setRefmapName(String refmapName) {
		getDeprecationHelper().replaceWithInLoom0_11("refmapName", "mixinRefmapName");
		getMixinRefmapName().set(refmapName);
	}

	Property<Boolean> getRemapArchives();

	@Deprecated(forRemoval = true)
	@ApiStatus.ScheduledForRemoval(inVersion = "0.11")
	default boolean isRemapMod() {
		getDeprecationHelper().replaceWithInLoom0_11("remapMod", "remapArchives");
		return getRemapArchives().get();
	}

	@Deprecated(forRemoval = true)
	@ApiStatus.ScheduledForRemoval(inVersion = "0.11")
	default void setRemapMod(boolean remapMod) {
		getDeprecationHelper().replaceWithInLoom0_11("remapMod", "remapArchives");
		getRemapArchives().set(remapMod);
	}

	void runs(Action<NamedDomainObjectContainer<RunConfigSettings>> action);

	NamedDomainObjectContainer<RunConfigSettings> getRunConfigs();

	@ApiStatus.Experimental
	void mixin(Action<MixinApExtensionAPI> action);

	Property<String> getCustomMinecraftManifest();

	@Deprecated(forRemoval = true)
	@ApiStatus.ScheduledForRemoval(inVersion = "0.11")
	default void setCustomManifest(String customManifest) {
		getDeprecationHelper().replaceWithInLoom0_11("customManifest", "customMinecraftManifest");
		getCustomMinecraftManifest().set(customManifest);
	}

	@Deprecated(forRemoval = true)
	@ApiStatus.ScheduledForRemoval(inVersion = "0.11")
	default String getCustomManifest() {
		getDeprecationHelper().replaceWithInLoom0_11("customManifest", "customMinecraftManifest");
		return getCustomMinecraftManifest().getOrNull();
	}
}
