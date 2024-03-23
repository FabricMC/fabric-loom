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

package net.fabricmc.loom.build.nesting;

import java.util.Locale;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.ArtifactView;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Provider;

public final class IncludedJarFactory {
	private final Project project;
	private final String prefix;


	public IncludedJarFactory(Task task) {
		this.project = task.getProject();
		this.prefix = task.getName();
	}

	public Provider<ConfigurableFileCollection> getNestedJars(final Configuration configuration) {
		ArtifactView artifacts = configuration.getIncoming().artifactView(config -> {
			config.attributes(
					attr -> attr.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE)
			);
		});
		String nestableModJsonTaskName = prefix + "NestableModJsons" + configuration.getName().substring(0, 1).toUpperCase(Locale.ROOT) + configuration.getName().substring(1);
		var nestableModJsonTask = project.getTasks().register(nestableModJsonTaskName, MinimalFabricJsonGenerator.class, task -> {
			task.from(artifacts.getArtifacts());
			task.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir("nestableJars/" + nestableModJsonTaskName));
		});
		String nestableJarTaskName = prefix + "NestableJars" + configuration.getName().substring(0, 1).toUpperCase(Locale.ROOT) + configuration.getName().substring(1);
		var nestableJarsTask = project.getTasks().register(nestableJarTaskName, NestableJarGenerationTask.class, task -> {
			task.getJars().from(artifacts.getFiles());
			task.getModJsons().from(project.fileTree(nestableModJsonTask.get().getOutputDirectory()));
			task.dependsOn(nestableModJsonTask);
			task.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir("nestableJars/" + nestableJarTaskName));
		});
		return project.provider(() -> {
			final ConfigurableFileCollection files = project.files();
			files.from(project.fileTree(nestableJarsTask.get().getOutputDirectory()));
			files.builtBy(nestableJarsTask);
			return files;
		});
	}
}
