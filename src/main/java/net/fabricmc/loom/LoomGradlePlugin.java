/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2021 FabricMC
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

import java.util.Objects;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.gradle.api.Project;
import org.gradle.api.plugins.PluginAware;

import net.fabricmc.loom.api.LoomGradleExtensionAPI;
import net.fabricmc.loom.bootstrap.BootstrappedPlugin;
import net.fabricmc.loom.configuration.CompileConfiguration;
import net.fabricmc.loom.configuration.FabricApiExtension;
import net.fabricmc.loom.configuration.MavenPublication;
import net.fabricmc.loom.configuration.ide.IdeConfiguration;
import net.fabricmc.loom.configuration.ide.intelij.IntelijConfiguration;
import net.fabricmc.loom.decompilers.DecompilerConfiguration;
import net.fabricmc.loom.extension.LoomFiles;
import net.fabricmc.loom.extension.LoomGradleExtensionImpl;
import net.fabricmc.loom.task.LoomTasks;

public class LoomGradlePlugin implements BootstrappedPlugin {
	public static boolean refreshDeps;
	public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	public static final String LOOM_VERSION = Objects.requireNonNullElse(LoomGradlePlugin.class.getPackage().getImplementationVersion(), "0.0.0+unknown");

	@Override
	public void apply(PluginAware target) {
		target.getPlugins().apply(LoomRepositoryPlugin.class);

		if (target instanceof Project project) {
			apply(project);
		}
	}

	public void apply(Project project) {
		project.getLogger().lifecycle("Fabric Loom: " + LOOM_VERSION);

		refreshDeps = project.getGradle().getStartParameter().isRefreshDependencies() || Boolean.getBoolean("loom.refresh");

		if (refreshDeps) {
			project.getLogger().lifecycle("Refresh dependencies is in use, loom will be significantly slower.");
		}

		// Apply default plugins
		project.apply(ImmutableMap.of("plugin", "java-library"));
		project.apply(ImmutableMap.of("plugin", "eclipse"));
		project.apply(ImmutableMap.of("plugin", "idea"));

		// Setup extensions
		project.getExtensions().create(LoomGradleExtensionAPI.class, "loom", LoomGradleExtensionImpl.class, project, LoomFiles.create(project));
		project.getExtensions().create("fabricApi", FabricApiExtension.class, project);

		CompileConfiguration.setupConfigurations(project);
		IdeConfiguration.setup(project);
		CompileConfiguration.configureCompile(project);
		MavenPublication.configure(project);
		LoomTasks.registerTasks(project);
		DecompilerConfiguration.setup(project);
		IntelijConfiguration.setup(project);
	}
}
