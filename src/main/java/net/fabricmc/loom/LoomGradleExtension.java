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

package net.fabricmc.loom;

import org.gradle.api.Project;

import net.fabricmc.loom.extension.internal.ConfigurationInternalGradleExtension;
import net.fabricmc.loom.extension.internal.InstallerInternalGradleExtension;
import net.fabricmc.loom.extension.internal.MappingCacheInternalGradleExtension;
import net.fabricmc.loom.extension.internal.MixinInternalGradleExtension;
import net.fabricmc.loom.extension.internal.ProviderInternalGradleExtension;
import net.fabricmc.loom.api.extension.LoomGradleExtensionAPI;
import net.fabricmc.loom.extension.LoomDirectories;
import net.fabricmc.loom.extension.LoomGradleExtensionImpl;

public interface LoomGradleExtension extends LoomGradleExtensionAPI, ConfigurationInternalGradleExtension, InstallerInternalGradleExtension, MappingCacheInternalGradleExtension, ProviderInternalGradleExtension, MixinInternalGradleExtension {
	static LoomGradleExtension get(Project project) {
		return project.getExtensions().getByType(LoomGradleExtensionImpl.class);
	}

	LoomDirectories getDirectories();

	// TODO bellow this is just misc stuff, might need moving

	boolean isRootProject();

	boolean isShareCaches();

	default boolean ideSync() {
		return Boolean.parseBoolean(System.getProperty("idea.sync.active", "false"));
	}

	default String getCustomManifest() {
		// TODO reimplement
		return null;
	}

	default String getIntermediaryUrl(String minecraftVersion) {
		// TODO reimplement a way to change this, was never really supported api anyway
		return String.format("https://maven.fabricmc.net/net/fabricmc/intermediary/%1$s/intermediary-%1$s-v2.jar", minecraftVersion);
	}
}
