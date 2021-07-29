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

package net.fabricmc.loom.extension;

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

import net.fabricmc.loom.api.LoomGradleExtensionAPI;
import net.fabricmc.loom.api.MixinApExtensionAPI;
import net.fabricmc.loom.api.decompilers.LoomDecompiler;
import net.fabricmc.loom.configuration.ide.RunConfigSettings;
import net.fabricmc.loom.configuration.processors.JarProcessor;
import net.fabricmc.loom.configuration.providers.mappings.LayeredMappingSpecBuilder;
import net.fabricmc.loom.util.DeprecationHelper;

public class MinecraftGradleExtension implements LoomGradleExtensionAPI {
	private final LoomGradleExtensionAPI parent;
	private boolean deprecationReported = false;

	public MinecraftGradleExtension(LoomGradleExtensionAPI parent) {
		this.parent = parent;
	}

	private void reportDeprecation() {
		if (!deprecationReported) {
			getDeprecationHelper().replaceWithInLoom0_11("minecraft", "loom");
			deprecationReported = true;
		}
	}

	@Override
	public DeprecationHelper getDeprecationHelper() {
		return parent.getDeprecationHelper();
	}

	@Override
	public RegularFileProperty getAccessWidenerPath() {
		reportDeprecation();
		return parent.getAccessWidenerPath();
	}

	@Override
	public Property<Boolean> getShareRemapCaches() {
		reportDeprecation();
		return parent.getShareRemapCaches();
	}

	@Override
	public ListProperty<LoomDecompiler> getGameDecompilers() {
		reportDeprecation();
		return parent.getGameDecompilers();
	}

	@Override
	public ListProperty<JarProcessor> getGameJarProcessors() {
		reportDeprecation();
		return parent.getGameJarProcessors();
	}

	@Override
	public ConfigurableFileCollection getLog4jConfigs() {
		reportDeprecation();
		return parent.getLog4jConfigs();
	}

	@Override
	public Dependency layered(Action<LayeredMappingSpecBuilder> action) {
		reportDeprecation();
		return parent.layered(action);
	}

	@Override
	public Property<Boolean> getRemapArchives() {
		reportDeprecation();
		return parent.getRemapArchives();
	}

	@Override
	public void runs(Action<NamedDomainObjectContainer<RunConfigSettings>> action) {
		reportDeprecation();
		parent.runs(action);
	}

	@Override
	public NamedDomainObjectContainer<RunConfigSettings> getRunConfigs() {
		reportDeprecation();
		return parent.getRunConfigs();
	}

	@Override
	public void mixin(Action<MixinApExtensionAPI> action) {
		reportDeprecation();
		parent.mixin(action);
	}

	@Override
	public MixinApExtensionAPI getMixin() {
		reportDeprecation();
		return parent.getMixin();
	}

	@Override
	public Property<String> getCustomMinecraftManifest() {
		reportDeprecation();
		return parent.getCustomMinecraftManifest();
	}
}
