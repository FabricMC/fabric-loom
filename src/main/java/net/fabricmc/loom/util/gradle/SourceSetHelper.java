/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022 FabricMC
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

package net.fabricmc.loom.util.gradle;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.tasks.DefaultSourceSetOutput;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetOutput;
import org.gradle.api.tasks.TaskProvider;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.xml.sax.InputSource;

import net.fabricmc.loom.api.ModSettings;
import net.fabricmc.loom.configuration.ide.idea.IdeaUtils;

public final class SourceSetHelper {
	@VisibleForTesting
	@Language("xpath")
	public static final String IDEA_OUTPUT_XPATH = "/project/component[@name='ProjectRootManager']/output/@url";

	private SourceSetHelper() {
	}

	/**
	 * Returns true when the provided project contains the {@link SourceSet}.
	 */
	public static boolean isSourceSetOfProject(SourceSet sourceSet, Project project) {
		if (System.getProperty("fabric-loom.unit.testing") != null) return true;

		final JavaPluginExtension javaExtension = project.getExtensions().getByType(JavaPluginExtension.class);
		return javaExtension.getSourceSets().stream()
				.anyMatch(test -> test == sourceSet); // Ensure we have an identical reference
	}

	/**
	 * Attempts to compute the owning project for the {@link SourceSet}
	 *
	 * <p>A bit of a hack, would be nice for this to be added to the Gradle API.
	 */
	public static Project getSourceSetProject(SourceSet sourceSet) {
		final DefaultSourceSetOutput sourceSetOutput = (DefaultSourceSetOutput) sourceSet.getOutput();
		final DefaultTaskDependency taskDependency = (DefaultTaskDependency) sourceSetOutput.getClassesContributors();
		Project project = null;

		for (Object object : taskDependency.getMutableValues()) {
			if (object instanceof Task task) {
				project = task.getProject();
			} else if (object instanceof TaskProvider<?> provider) {
				project = provider.get().getProject();
			}
		}

		if (project == null) {
			throw new NullPointerException("Unable to determine owning project for SourceSet: " + sourceSet.getName());
		}

		return project;
	}

	public static List<File> getClasspath(ModSettings modSettings, Project project) {
		final List<File> files = new ArrayList<>();

		files.addAll(modSettings.getModSourceSets().get().stream()
				.flatMap(sourceSet -> getClasspath(sourceSet, project).stream())
				.toList());
		files.addAll(modSettings.getModFiles().getFiles());

		return Collections.unmodifiableList(files);
	}

	public static List<File> getClasspath(SourceSetReference reference, Project project) {
		final List<File> classpath = getGradleClasspath(reference);

		classpath.addAll(getIdeaClasspath(reference, project));
		classpath.addAll(getEclipseClasspath(reference, project));
		classpath.addAll(getVscodeClasspath(reference, project));

		return classpath;
	}

	private static List<File> getGradleClasspath(SourceSetReference reference) {
		final SourceSetOutput output = reference.sourceSet().getOutput();
		final File resources = output.getResourcesDir();

		final List<File> classpath = new ArrayList<>();

		classpath.addAll(output.getClassesDirs().getFiles());

		if (resources != null) {
			classpath.add(resources);
		}

		return classpath;
	}

	@VisibleForTesting
	public static List<File> getIdeaClasspath(SourceSetReference reference, Project project) {
		final File projectDir = project.getRootDir();
		final File dotIdea = new File(projectDir, ".idea");

		if (!dotIdea.exists()) {
			return Collections.emptyList();
		}

		final File miscXml = new File(dotIdea, "misc.xml");

		if (!miscXml.exists()) {
			return Collections.emptyList();
		}

		String outputDirUrl = evaluateXpath(miscXml, IDEA_OUTPUT_XPATH);

		if (outputDirUrl == null) {
			return Collections.emptyList();
		}

		outputDirUrl = outputDirUrl.replace("$PROJECT_DIR$", projectDir.getAbsolutePath());
		outputDirUrl = outputDirUrl.replaceAll("^file:", "");

		final File productionDir = new File(outputDirUrl, "production");
		final File outputDir = new File(productionDir, IdeaUtils.getIdeaModuleName(reference));

		return Collections.singletonList(outputDir);
	}

	@Nullable
	private static String evaluateXpath(File file, @Language("xpath") String expression) {
		final XPath xpath = XPathFactory.newInstance().newXPath();

		try (FileInputStream fis = new FileInputStream(file)) {
			String result = xpath.evaluate(expression, new InputSource(fis));

			if (result.isEmpty()) {
				return null;
			}

			return result;
		} catch (XPathExpressionException e) {
			return null;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@VisibleForTesting
	public static List<File> getEclipseClasspath(SourceSetReference reference, Project project) {
		// Somewhat of a guess, I'm unsure if this is correct for multi-project builds
		final File projectDir = project.getProjectDir();
		final File classpath = new File(projectDir, ".classpath");

		if (!classpath.exists()) {
			return Collections.emptyList();
		}

		return getBinDirClasspath(projectDir, reference);
	}

	@VisibleForTesting
	public static List<File> getVscodeClasspath(SourceSetReference reference, Project project) {
		// Somewhat of a guess, I'm unsure if this is correct for multi-project builds
		final File projectDir = project.getProjectDir();
		final File dotVscode = new File(projectDir, ".vscode");

		if (!dotVscode.exists()) {
			return Collections.emptyList();
		}

		return getBinDirClasspath(projectDir, reference);
	}

	private static List<File> getBinDirClasspath(File projectDir, SourceSetReference reference) {
		final File binDir = new File(projectDir, "bin");

		if (!binDir.exists()) {
			return Collections.emptyList();
		}

		return Collections.singletonList(new File(binDir, reference.sourceSet().getName()));
	}
}
