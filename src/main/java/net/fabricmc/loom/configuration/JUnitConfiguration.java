/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2023 FabricMC
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.util.GradleVersion;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.task.AbstractLoomTask;
import net.fabricmc.loom.task.launch.GenerateDLIConfigTask;

public class JUnitConfiguration {
	public static void setup(Project project) {
		if (!isGradle8OrHigher()) {
			// Only supported with Gradle 8
			return;
		}

		final LoomGradleExtension extension = LoomGradleExtension.get(project);
		final TaskContainer tasks = project.getTasks();

		var generateConfigTask = tasks.register("generateJUnitConfig", JUnitConfigTask.class, task -> {
			var props = task.getProperties();

			// TODO refactor this to not duplicate/call to GenerateDLIConfigTask
			props.put("fabric.remapClasspathFile", extension.getFiles().getRemapClasspathFile().getAbsolutePath());
			props.put("log4j.configurationFile", GenerateDLIConfigTask.getAllLog4JConfigFiles(extension));
			props.put("log4j2.formatMsgNoLookups", "true");

			if (extension.areEnvironmentSourceSetsSplit()) {
				props.put("fabric.gameJarPath.client", GenerateDLIConfigTask.getGameJarPath(extension, "client"));
				props.put("fabric.gameJarPath", GenerateDLIConfigTask.getGameJarPath(extension, "common"));
			}

			if (!extension.getMods().isEmpty()) {
				props.put("fabric.classPathGroups", GenerateDLIConfigTask.getClassPathGroups(extension, project));
			}

			task.getOutput().set(new File(extension.getFiles().getProjectPersistentCache(), "junit-test-resources"));
		});

		tasks.named("configureLaunch", configureLaunch -> configureLaunch.dependsOn(generateConfigTask));
		tasks.named("ideaSyncTask", configureLaunch -> configureLaunch.dependsOn(generateConfigTask));
		tasks.named("test", test -> test.dependsOn(generateConfigTask));

		project.getDependencies().add("testRuntimeOnly", project.files(generateConfigTask.map(JUnitConfigTask::getOutput)));
	}

	private static boolean isGradle8OrHigher() {
		return getMajorGradleVersion() >= 8;
	}

	private static int getMajorGradleVersion() {
		String version = GradleVersion.current().getVersion();
		return Integer.parseInt(version.substring(0, version.indexOf(".")));
	}

	public abstract static class JUnitConfigTask extends AbstractLoomTask {
		private static final String PROPERTY_FILE_NAME = "fabric-loader-junit.properties";

		@Input
		public abstract MapProperty<String, String> getProperties();

		@OutputDirectory
		public abstract DirectoryProperty getOutput();

		@TaskAction
		public void run() throws IOException {
			final String content = getProperties().get().entrySet().stream()
					.map(e -> "%s=%s".formatted(e.getKey(), e.getValue()))
					.collect(Collectors.joining("\n"));
			final Path outputDir = getOutput().get().getAsFile().toPath();

			Files.createDirectories(outputDir);
			Files.writeString(outputDir.resolve(PROPERTY_FILE_NAME), content);
		}
	}
}
