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
import org.jetbrains.annotations.ApiStatus;

import net.fabricmc.loom.api.decompilers.LoomDecompiler;
import net.fabricmc.loom.configuration.ide.RunConfigSettings;
import net.fabricmc.loom.configuration.processors.JarProcessor;
import net.fabricmc.loom.configuration.providers.mappings.LayeredMappingSpecBuilder;

/**
 * This is the public api available exposed to build scripts.
 */
public interface LoomGradleExtensionAPI {
	File getAccessWidener();

	void setAccessWidener(Object file);

	void setShareCaches(boolean shareCaches);

	boolean isShareCaches();

	default void shareCaches() {
		setShareCaches(true);
	}

	List<LoomDecompiler> getDecompilers();

	void addDecompiler(LoomDecompiler decompiler);

	List<JarProcessor> getJarProcessors();

	void addJarProcessor(JarProcessor processor);

	ConfigurableFileCollection getLog4jConfigs();

	default Dependency officialMojangMappings() {
		return layered(LayeredMappingSpecBuilder::officialMojangMappings);
	}

	Dependency layered(Action<LayeredMappingSpecBuilder> action);

	String getRefmapName();

	void setRefmapName(String refmapName);

	boolean isRemapMod();

	void setRemapMod(boolean remapMod);

	void runs(Action<NamedDomainObjectContainer<RunConfigSettings>> action);

	NamedDomainObjectContainer<RunConfigSettings> getRunConfigs();

	@ApiStatus.Experimental
	void mixin(Action<MixinApExtensionAPI> action);

	void setCustomManifest(String customManifest);

	String getCustomManifest();
}
