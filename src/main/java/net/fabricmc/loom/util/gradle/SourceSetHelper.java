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
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetOutput;
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

	public static List<File> getClasspath(ModSettings modSettings, Project project) {
		final List<File> files = new ArrayList<>();

		files.addAll(modSettings.getModSourceSets().get().stream()
				.flatMap(sourceSet -> getClasspath(sourceSet, project).stream())
				.toList());
		files.addAll(modSettings.getModFiles().getFiles());

		return Collections.unmodifiableList(files);
	}

	public static List<File> getClasspath(SourceSet sourceSet, Project project) {
		final List<File> classpath = getGradleClasspath(sourceSet);

		classpath.addAll(getIdeaClasspath(sourceSet, project));
		classpath.addAll(getEclipseClasspath(sourceSet, project));
		classpath.addAll(getVscodeClasspath(sourceSet, project));

		return classpath;
	}

	private static List<File> getGradleClasspath(SourceSet sourceSet) {
		final SourceSetOutput output = sourceSet.getOutput();
		final File resources = output.getResourcesDir();

		final List<File> classpath = new ArrayList<>();

		classpath.addAll(output.getClassesDirs().getFiles());

		if (resources != null) {
			classpath.add(resources);
		}

		return classpath;
	}

	@VisibleForTesting
	public static List<File> getIdeaClasspath(SourceSet sourceSet, Project project) {
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
		final File outputDir = new File(productionDir, IdeaUtils.getIdeaModuleName(project, sourceSet));

		return Collections.singletonList(outputDir);
	}

	@Nullable
	private static String evaluateXpath(File file, @Language("xpath") String expression) {
		final XPath xpath = XPathFactory.newInstance().newXPath();

		try (FileInputStream fis = new FileInputStream(file)) {
			return xpath.evaluate(expression, new InputSource(fis));
		} catch (XPathExpressionException e) {
			return null;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@VisibleForTesting
	public static List<File> getEclipseClasspath(SourceSet sourceSet, Project project) {
		// Somewhat of a guess, I'm unsure if this is correct for multi-project builds
		final File projectDir = project.getProjectDir();
		final File classpath = new File(projectDir, ".classpath");

		if (!classpath.exists()) {
			return Collections.emptyList();
		}

		return getBinDirClasspath(projectDir, sourceSet);
	}

	@VisibleForTesting
	public static List<File> getVscodeClasspath(SourceSet sourceSet, Project project) {
		// Somewhat of a guess, I'm unsure if this is correct for multi-project builds
		final File projectDir = project.getProjectDir();
		final File dotVscode = new File(projectDir, ".vscode");

		if (!dotVscode.exists()) {
			return Collections.emptyList();
		}

		return getBinDirClasspath(projectDir, sourceSet);
	}

	private static List<File> getBinDirClasspath(File projectDir, SourceSet sourceSet) {
		final File binDir = new File(projectDir, "bin");

		if (!binDir.exists()) {
			return Collections.emptyList();
		}

		return Collections.singletonList(new File(binDir, sourceSet.getName()));
	}
}
