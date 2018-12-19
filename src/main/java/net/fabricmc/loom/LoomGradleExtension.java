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

import net.fabricmc.loom.providers.MappingsProvider;
import net.fabricmc.loom.providers.MinecraftMappedProvider;
import net.fabricmc.loom.providers.MinecraftProvider;
import net.fabricmc.loom.util.LoomDependencyManager;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class LoomGradleExtension {
	public String runDir = "run";
	public String refmapName;
	public boolean remapMod = true;
	public boolean autoGenIDERuns = true;

	private List<File> unmappedModsBuilt = new ArrayList<>();

	//Not to be set in the build.gradle
	private Project project;
	private LoomDependencyManager dependencyManager;

	public LoomGradleExtension(Project project) {
		this.project = project;
	}

	public void addUnmappedMod(File file) {
		unmappedModsBuilt.add(file);
	}

	public List<File> getUnmappedMods() {
		return Collections.unmodifiableList(unmappedModsBuilt);
	}

	public File getUserCache() {
		File userCache = new File(project.getGradle().getGradleUserHomeDir(), "caches" + File.separator + "fabric-loom");
		if (!userCache.exists()) {
			userCache.mkdirs();
		}
		return userCache;
	}

	public File getProjectCache(){
		File projectCache = new File(project.getRootDir(), ".gradle/minecraft/");
		if(!projectCache.exists()){
			projectCache.mkdirs();
		}
		return projectCache;
	}

	@Nullable
	public String getMixinVersion() {
		for (Dependency dependency : project.getConfigurations().getByName("compile").getDependencies()) {
			if (dependency.getName().equalsIgnoreCase("mixin") && dependency.getGroup().equals("org.spongepowered")) {
				return dependency.getVersion();
			}

			if (dependency.getName().equals("sponge-mixin") && dependency.getGroup().equals("net.fabricmc")) {
				if (Objects.requireNonNull(dependency.getVersion()).split("\\.").length >= 4) {
					return dependency.getVersion().substring(0, dependency.getVersion().lastIndexOf('.')) + "-SNAPSHOT";
				}
				return dependency.getVersion();
			}
		}

		return null;
	}

	public LoomDependencyManager getDependencyManager() {
		return dependencyManager;
	}

	public MinecraftProvider getMinecraftProvider(){
		return getDependencyManager().getProvider(MinecraftProvider.class);
	}

	public MinecraftMappedProvider getMinecraftMappedProvider(){
		return getMappingsProvider().mappedProvider;
	}

	public MappingsProvider getMappingsProvider(){
		return getDependencyManager().getProvider(MappingsProvider.class);
	}

	public void setDependencyManager(LoomDependencyManager dependencyManager) {
		this.dependencyManager = dependencyManager;
	}
}
