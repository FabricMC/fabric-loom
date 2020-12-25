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

import com.google.common.collect.ImmutableMap;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

import net.fabricmc.loom.configuration.CompileConfiguration;
import net.fabricmc.loom.configuration.FabricApiExtension;
import net.fabricmc.loom.configuration.MavenPublication;
import net.fabricmc.loom.configuration.ide.IdeConfiguration;
import net.fabricmc.loom.configuration.providers.mappings.MappingsCache;
import net.fabricmc.loom.decompilers.DecompilerConfiguration;
import net.fabricmc.loom.task.LoomTasks;

public class LoomGradlePlugin implements Plugin<Project> {
	public static boolean refreshDeps;

	@Override
	public void apply(Project project) {
		project.getLogger().lifecycle("Fabric Loom: " + LoomGradlePlugin.class.getPackage().getImplementationVersion());

		refreshDeps = project.getGradle().getStartParameter().isRefreshDependencies();

		if (refreshDeps) {
			MappingsCache.INSTANCE.invalidate();
			project.getLogger().lifecycle("Refresh dependencies is in use, loom will be significantly slower.");
		}
		/* Replacement below

		tasks.register("runClient", RunClientTask.class, t -> {
			t.setDescription("Starts a development version of the Minecraft client.");
			t.dependsOn("downloadAssets");
			t.setGroup("fabric");
		});

		// Apply default plugins
		project.apply(ImmutableMap.of("plugin", "java"));
		project.apply(ImmutableMap.of("plugin", "eclipse"));
		project.apply(ImmutableMap.of("plugin", "idea"));
		tasks.register("runServer", RunServerTask.class, t -> {
			t.setDescription("Starts a development version of the Minecraft server.");
			t.setGroup("fabric");
		});
		*/

		// Setup extensions, loom shadows minecraft
		project.getExtensions().create("minecraft", LoomGradleExtension.class, project);
		project.getExtensions().add("loom", project.getExtensions().getByName("minecraft"));
		project.getExtensions().create("fabricApi", FabricApiExtension.class, project);

		CompileConfiguration.setupConfigurations(project);
		IdeConfiguration.setup(project);
		CompileConfiguration.configureCompile(project);
		MavenPublication.configure(project);
		LoomTasks.registerTasks(project);
		DecompilerConfiguration.setup(project);
		project.afterEvaluate((p) -> {
			for (LoomDecompiler decompiler : extension.decompilers) {
				String taskName = (decompiler instanceof FabricFernFlowerDecompiler) ? "genSources" : "genSourcesWith" + decompiler.name();
				// decompiler will be passed to the constructor of GenerateSourcesTask
				tasks.register(taskName, GenerateSourcesTask.class, decompiler);
			}
		});

		// Default run configurations
		extension.getRuns().create("client");
		extension.getRuns().create("server");

		project.afterEvaluate((p) -> {
			for (LoomGradleExtension.RunConfigSettings config : extension.getRuns()) {
				String configName = config.getName();
				String taskName = "run" + configName.substring(0, 1).toUpperCase() + configName.substring(1);

				tasks.register(taskName, RunGameTask.class, config).configure(t -> {
					t.setDescription("Starts a development version of the Minecraft server.");
					t.setGroup("fabric");
				});
			}
		});
	}
}
