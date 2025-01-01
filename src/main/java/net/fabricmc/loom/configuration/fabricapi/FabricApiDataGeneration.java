/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2024 FabricMC
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

package net.fabricmc.loom.configuration.fabricapi;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.jvm.tasks.Jar;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.fabricapi.DataGenerationSettings;
import net.fabricmc.loom.util.gradle.SourceSetHelper;

public abstract class FabricApiDataGeneration extends FabricApiAbstractSourceSet {
	@Inject
	protected abstract Project getProject();

	@Inject
	public FabricApiDataGeneration() {
	}

	@Override
	protected String getSourceSetName() {
		return "datagen";
	}

	void configureDataGeneration(Action<DataGenerationSettings> action) {
		final LoomGradleExtension extension = LoomGradleExtension.get(getProject());
		final TaskContainer taskContainer = getProject().getTasks();

		DataGenerationSettings settings = getProject().getObjects().newInstance(DataGenerationSettings.class);
		settings.getOutputDirectory().set(getProject().file("src/main/generated"));
		settings.getCreateRunConfiguration().convention(true);
		settings.getCreateSourceSet().convention(false);
		settings.getStrictValidation().convention(false);
		settings.getAddToResources().convention(true);
		settings.getClient().convention(false);

		action.execute(settings);

		final SourceSet mainSourceSet = SourceSetHelper.getMainSourceSet(getProject());
		final File outputDirectory = settings.getOutputDirectory().getAsFile().get();

		if (settings.getAddToResources().get()) {
			mainSourceSet.resources(files -> {
				// Add the src/main/generated to the main sourceset's resources.
				Set<File> srcDirs = new HashSet<>(files.getSrcDirs());
				srcDirs.add(outputDirectory);
				files.setSrcDirs(srcDirs);
			});
		}

		// Exclude the cache dir from the output jar to ensure reproducibility.
		taskContainer.getByName(JavaPlugin.JAR_TASK_NAME, task -> {
			Jar jar = (Jar) task;
			jar.exclude(".cache/**");
		});

		if (settings.getCreateSourceSet().get()) {
			configureSourceSet(settings.getModId(), settings.getClient().get());
		}

		if (settings.getCreateRunConfiguration().get()) {
			extension.getRunConfigs().create("datagen", run -> {
				run.inherit(extension.getRunConfigs().getByName(settings.getClient().get() ? "client" : "server"));
				run.setConfigName("Data Generation");

				run.property("fabric-api.datagen");
				run.property("fabric-api.datagen.output-dir", outputDirectory.getAbsolutePath());
				run.runDir("build/datagen");

				if (settings.getModId().isPresent()) {
					run.property("fabric-api.datagen.modid", settings.getModId().get());
				}

				if (settings.getStrictValidation().get()) {
					run.property("fabric-api.datagen.strict-validation", "true");
				}

				if (settings.getCreateSourceSet().get()) {
					run.source(getSourceSetName());
				}
			});

			// Add the output directory as an output allowing the task to be skipped.
			getProject().getTasks().named("runDatagen", task -> {
				task.getOutputs().dir(outputDirectory);
			});
		}
	}

	private static void extendsFrom(Project project, String name, String extendsFrom) {
		final ConfigurationContainer configurations = project.getConfigurations();

		configurations.named(name, configuration -> {
			configuration.extendsFrom(configurations.getByName(extendsFrom));
		});
	}

	private void dependsOn(SourceSet sourceSet, SourceSet other) {
		sourceSet.setCompileClasspath(
				sourceSet.getCompileClasspath()
						.plus(other.getOutput())
		);

		sourceSet.setRuntimeClasspath(
				sourceSet.getRuntimeClasspath()
						.plus(other.getOutput())
		);

		extendsFrom(getProject(), sourceSet.getCompileClasspathConfigurationName(), other.getCompileClasspathConfigurationName());
		extendsFrom(getProject(), sourceSet.getRuntimeClasspathConfigurationName(), other.getRuntimeClasspathConfigurationName());
	}
}
