/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2020 FabricMC
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

package net.fabricmc.loom.build.mixin;

import java.io.File;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.JavaCompile;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.extension.MixinApExtension;

public class JavaApInvoker extends AnnotationProcessorInvoker<JavaCompile> {
	public JavaApInvoker(Project project) {
		super(
				project,
				AnnotationProcessorInvoker.getApConfigurations(project, JavaApInvoker::getAptConfigurationName),
				getInvokerTasks(project));
	}

	private static Map<SourceSet, JavaCompile> getInvokerTasks(Project project) {
		MixinApExtension mixin = LoomGradleExtension.get(project).getMixinApExtension();
		return mixin.getInvokerTasksStream(AnnotationProcessorInvoker.JAVA)
				.collect(Collectors.toMap(Map.Entry::getKey, entry -> Objects.requireNonNull((JavaCompile) entry.getValue())));
	}

	@Override
	protected void passArgument(JavaCompile compileTask, String key, String value) {
		compileTask.getOptions().getCompilerArgs().add("-A" + key + "=" + value);
	}

	@Override
	protected File getRefmapDestinationDir(JavaCompile task) {
		return task.getDestinationDir();
	}

	private static String getAptConfigurationName(String sourceSet) {
		// This is documented by the gradle 4.6 release notes https://docs.gradle.org/4.6/release-notes.html#potential-breaking-changes
		return sourceSet.equals("main") ? JavaPlugin.ANNOTATION_PROCESSOR_CONFIGURATION_NAME : sourceSet + "AnnotationProcessor";
	}
}
