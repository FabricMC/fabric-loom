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
import org.gradle.api.model.ReplacedBy;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.jetbrains.annotations.ApiStatus;

import net.fabricmc.loom.api.decompilers.LoomDecompiler;
import net.fabricmc.loom.configuration.ide.RunConfigSettings;
import net.fabricmc.loom.configuration.processors.JarProcessor;
import net.fabricmc.loom.configuration.providers.mappings.LayeredMappingSpecBuilder;

/**
 * This is the public api available exposed to build scripts.
 */
public interface LoomGradleExtensionAPI {
	RegularFileProperty getAccessWidenerPath();

	@Deprecated
	@ReplacedBy("accessWidenerPath")
	default File getAccessWidener() {
		return getAccessWidenerPath().getAsFile().getOrNull();
	}

	@Deprecated
	@ReplacedBy("accessWidenerPath")
	void setAccessWidener(Object file);

	Property<Boolean> getShareCaches();

	@Deprecated
	@ReplacedBy("shareCaches")
	default void setShareCaches(boolean shareCaches) {
		getShareCaches().set(shareCaches);
	}

	@Deprecated
	@ReplacedBy("shareCaches")
	default boolean isShareCaches() {
		return getShareCaches().get();
	}

	@Deprecated
	@ReplacedBy("shareCaches")
	default void shareCaches() {
		setShareCaches(true);
	}

	ListProperty<LoomDecompiler> getLoomDecompilers();

	@Deprecated
	@ReplacedBy("loomDecompilers")
	default List<LoomDecompiler> getDecompilers() {
		return getLoomDecompilers().get();
	}

	@Deprecated
	@ReplacedBy("loomDecompilers")
	default void addDecompiler(LoomDecompiler decompiler) {
		getLoomDecompilers().add(decompiler);
	}

	ListProperty<JarProcessor> getMinecraftJarProcessors();

	@Deprecated
	@ReplacedBy("minecraftJarProcessors")
	default List<JarProcessor> getJarProcessors() {
		return getMinecraftJarProcessors().get();
	}

	@Deprecated
	@ReplacedBy("minecraftJarProcessors")
	default void addJarProcessor(JarProcessor processor) {
		getMinecraftJarProcessors().add(processor);
	}

	ConfigurableFileCollection getLog4jConfigs();

	default Dependency officialMojangMappings() {
		return layered(LayeredMappingSpecBuilder::officialMojangMappings);
	}

	Dependency layered(Action<LayeredMappingSpecBuilder> action);

	Property<String> getMixinRefmapName();

	@Deprecated
	@ReplacedBy("mixinRefmapName")
	default String getRefmapName() {
		return getMixinRefmapName().get();
	}

	@Deprecated
	@ReplacedBy("mixinRefmapName")
	default void setRefmapName(String refmapName) {
		getMixinRefmapName().set(refmapName);
	}

	Property<Boolean> getRemapArchives();

	@Deprecated
	@ReplacedBy("remapArchives")
	default boolean isRemapMod() {
		return getRemapArchives().get();
	}

	@Deprecated
	@ReplacedBy("remapArchives")
	default void setRemapMod(boolean remapMod) {
		getRemapArchives().set(remapMod);
	}

	void runs(Action<NamedDomainObjectContainer<RunConfigSettings>> action);

	NamedDomainObjectContainer<RunConfigSettings> getRunConfigs();

	@ApiStatus.Experimental
	void mixin(Action<MixinApExtensionAPI> action);

	Property<String> getCustomMinecraftManifest();

	@Deprecated
	@ReplacedBy("customMinecraftManifest")
	default void setCustomManifest(String customManifest) {
		getCustomMinecraftManifest().set(customManifest);
	}

	@Deprecated
	@ReplacedBy("customMinecraftManifest")
	default String getCustomManifest() {
		return getCustomMinecraftManifest().getOrNull();
	}
}
