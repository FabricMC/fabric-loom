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

package net.fabricmc.loom.configuration;

import java.io.IOException;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.build.JarRemapper;
import net.fabricmc.loom.build.nesting.NestedDependencyProvider;
import net.fabricmc.loom.task.AbstractLoomTask;
import net.fabricmc.loom.task.RemapAllSourcesTask;
import net.fabricmc.loom.task.RemapJarTask;
import net.fabricmc.loom.task.RemapSourcesJarTask;
import net.fabricmc.loom.util.SourceRemapper;

public class RemapConfiguration {
	public static void setupDefaultRemap(Project project) {
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		AbstractArchiveTask jarTask = (AbstractArchiveTask) project.getTasks().getByName("jar");
		RemapJarTask remapJarTask = (RemapJarTask) project.getTasks().findByName("remapJar");

		assert remapJarTask != null;

		if (!remapJarTask.getInput().isPresent()) {
			jarTask.setClassifier("dev");
			remapJarTask.setClassifier("");
			remapJarTask.getInput().set(jarTask.getArchivePath());
		}

		extension.getUnmappedModCollection().from(jarTask);
		remapJarTask.getAddNestedDependencies().set(true);
		remapJarTask.getRemapAccessWidener().set(true);

		project.getArtifacts().add("archives", remapJarTask);
		remapJarTask.dependsOn(jarTask);
		project.getTasks().getByName("build").dependsOn(remapJarTask);

		project.getTasks().withType(RemapJarTask.class).forEach(task -> {
			if (task.getAddNestedDependencies().getOrElse(false)) {
				NestedDependencyProvider.getRequiredTasks(project).forEach(task::dependsOn);
			}
		});

		SourceRemapper remapper = null;
		Task parentTask = project.getTasks().getByName("build");

		if (extension.isShareCaches()) {
			Project rootProject = project.getRootProject();

			if (extension.isRootProject()) {
				SourceRemapper sourceRemapper = new SourceRemapper(rootProject, false);
				JarRemapper jarRemapper = new JarRemapper();

				remapJarTask.jarRemapper = jarRemapper;

				rootProject.getTasks().register("remapAllSources", RemapAllSourcesTask.class, task -> {
					task.sourceRemapper = sourceRemapper;
					task.doLast(t -> sourceRemapper.remapAll());
				});

				parentTask = rootProject.getTasks().getByName("remapAllSources");

				rootProject.getTasks().register("remapAllJars", AbstractLoomTask.class, task -> {
					task.doLast(t -> {
						try {
							jarRemapper.remap();
						} catch (IOException e) {
							throw new RuntimeException("Failed to remap jars", e);
						}
					});
				});
			} else {
				parentTask = rootProject.getTasks().getByName("remapAllSources");
				remapper = ((RemapAllSourcesTask) parentTask).sourceRemapper;

				remapJarTask.jarRemapper = ((RemapJarTask) rootProject.getTasks().getByName("remapJar")).jarRemapper;

				project.getTasks().getByName("build").dependsOn(parentTask);
				project.getTasks().getByName("build").dependsOn(rootProject.getTasks().getByName("remapAllJars"));
				rootProject.getTasks().getByName("remapAllJars").dependsOn(project.getTasks().getByName("remapJar"));
			}
		}

		try {
			AbstractArchiveTask sourcesTask = (AbstractArchiveTask) project.getTasks().getByName("sourcesJar");

			RemapSourcesJarTask remapSourcesJarTask = (RemapSourcesJarTask) project.getTasks().findByName("remapSourcesJar");
			remapSourcesJarTask.setInput(sourcesTask.getArchivePath());
			remapSourcesJarTask.setOutput(sourcesTask.getArchivePath());
			remapSourcesJarTask.doLast(task -> project.getArtifacts().add("archives", remapSourcesJarTask.getOutput()));
			remapSourcesJarTask.dependsOn(project.getTasks().getByName("sourcesJar"));

			if (extension.isShareCaches()) {
				remapSourcesJarTask.setSourceRemapper(remapper);
			}

			parentTask.dependsOn(remapSourcesJarTask);
		} catch (UnknownTaskException ignored) {
			// pass
		}
	}
}