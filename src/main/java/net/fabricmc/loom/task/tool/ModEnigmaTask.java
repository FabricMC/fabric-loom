/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 FabricMC
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

package net.fabricmc.loom.task.tool;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.inject.Inject;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.ProblemReporter;
import org.gradle.api.problems.Problems;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;
import org.gradle.process.ExecOperations;
import org.jetbrains.annotations.ApiStatus;

import net.fabricmc.loom.task.AbstractLoomTask;
import net.fabricmc.loom.util.LoomProblems;
import net.fabricmc.loom.util.LoomVersions;

/**
 * Add this task to a mod development environment to use Enigma against the game jars.
 * This can be used for writing mod-provided javadoc etc.
 *
 * <p>Usage:
 * {@snippet lang=groovy :
 * tasks.register('enigma', ModEnigmaTask) {
 * 	// Must be a single Enigma-formatted mapping file:
 * 	mappingFile = file('src/main/resources/my_mod_data.mapping')
 * }
 * }
 */
@UntrackedTask(because = "Enigma should always launch")
public abstract class ModEnigmaTask extends AbstractLoomTask {
	private static final ProblemId MAPPINGS_MISSING_PROBLEM = LoomProblems.problemId("mappings-missing", "Mapping file doesn't exist");
	private static final String ENIGMA_MAIN_CLASS = "cuchaz.enigma.gui.Main";

	// Must be a ListProperty because the order matters.
	@Input
	public abstract ListProperty<Path> getMinecraftJars();

	/**
	 * The mapping file path. It must be a single Enigma-formatted file.
	 */
	@OutputFile
	public abstract RegularFileProperty getMappingFile();

	/**
	 * The Enigma classpath. You can add any Enigma plugin files to this file collection.
	 */
	@Classpath
	public abstract ConfigurableFileCollection getToolClasspath();

	@ApiStatus.Internal
	@Inject
	protected abstract ExecOperations getExecOperations();

	@ApiStatus.Internal
	@Inject
	protected abstract Problems getProblems();

	public ModEnigmaTask() {
		getMinecraftJars().convention(getProject().provider(() -> getExtension().getMinecraftJars(getExtension().getProductionNamespaceEnum())));
		getToolClasspath().from(getEnigmaClasspath(getProject()));
	}

	private static FileCollection getEnigmaClasspath(Project project) {
		final Dependency enigmaDep = project.getDependencies().create(LoomVersions.ENIGMA_SWING.mavenNotation());
		return project.getConfigurations().detachedConfiguration(enigmaDep);
	}

	@TaskAction
	public void launch() {
		final Path mappingFile = getMappingFile().get().getAsFile().toPath().toAbsolutePath();

		if (Files.notExists(mappingFile)) {
			final ProblemReporter reporter = getProblems().getReporter();
			reporter.throwing(new RuntimeException("Mapping file " + mappingFile + " doesn't exist"), MAPPINGS_MISSING_PROBLEM,
					spec -> spec
							.fileLocation(mappingFile.toString())
							.solution("Create the missing mapping file. Remember to add it to the fabric.mod.json if needed!"));
		}

		getExecOperations().javaexec(spec -> {
			spec.getMainClass().set(ENIGMA_MAIN_CLASS);
			spec.setClasspath(getToolClasspath());
			spec.jvmArgs("-Xmx2048m");

			for (Path path : getMinecraftJars().get()) {
				spec.args("-jar", path.toAbsolutePath().toString());
			}

			spec.args("-mappings", mappingFile.toString());
		});
	}
}
