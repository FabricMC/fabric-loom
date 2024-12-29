/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2023 FabricMC
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

import java.util.List;
import java.util.Objects;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.gradle.api.Project;
import org.gradle.api.plugins.PluginAware;

import net.fabricmc.loom.api.LoomGradleExtensionAPI;
import net.fabricmc.loom.api.fabricapi.FabricApiExtension;
import net.fabricmc.loom.bootstrap.BootstrappedPlugin;
import net.fabricmc.loom.configuration.CompileConfiguration;
import net.fabricmc.loom.configuration.fabricapi.FabricApiExtensionImpl;
import net.fabricmc.loom.configuration.LoomConfigurations;
import net.fabricmc.loom.configuration.MavenPublication;
import net.fabricmc.loom.configuration.ide.idea.IdeaConfiguration;
import net.fabricmc.loom.configuration.sandbox.SandboxConfiguration;
import net.fabricmc.loom.decompilers.DecompilerConfiguration;
import net.fabricmc.loom.extension.LoomFiles;
import net.fabricmc.loom.extension.LoomGradleExtensionImpl;
import net.fabricmc.loom.task.LoomTasks;
import net.fabricmc.loom.task.RemapTaskConfiguration;
import net.fabricmc.loom.util.LibraryLocationLogger;

public class LoomGradlePlugin implements BootstrappedPlugin {
	public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	public static final String LOOM_VERSION = Objects.requireNonNullElse(LoomGradlePlugin.class.getPackage().getImplementationVersion(), "0.0.0+unknown");

	/**
	 * An ordered list of setup job classes.
	 */
	private static final List<Class<? extends Runnable>> SETUP_JOBS = List.of(
			LoomConfigurations.class,
			CompileConfiguration.class,
			MavenPublication.class,
			RemapTaskConfiguration.class,
			LoomTasks.class,
			DecompilerConfiguration.class,
			IdeaConfiguration.class,
			SandboxConfiguration.class
	);

	@Override
	public void apply(PluginAware target) {
		target.getPlugins().apply(LoomRepositoryPlugin.class);

		if (target instanceof Project project) {
			apply(project);
		}
	}

	public void apply(Project project) {
		project.getLogger().lifecycle("Fabric Loom: " + LOOM_VERSION);
		LibraryLocationLogger.logLibraryVersions();

		// Apply default plugins
		project.apply(ImmutableMap.of("plugin", "java-library"));
		project.apply(ImmutableMap.of("plugin", "eclipse"));

		// Setup extensions
		project.getExtensions().create(LoomGradleExtensionAPI.class, "loom", LoomGradleExtensionImpl.class, project, LoomFiles.create(project));
		project.getExtensions().create(FabricApiExtension.class, "fabricApi", FabricApiExtensionImpl.class);

		for (Class<? extends Runnable> jobClass : SETUP_JOBS) {
			project.getObjects().newInstance(jobClass).run();
		}
	}
}
