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

import com.google.common.collect.ImmutableList;
import org.gradle.api.Project;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.scala.ScalaCompile;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.extension.MixinExtension;

public class ScalaApInvoker extends AnnotationProcessorInvoker<ScalaCompile> {
	public ScalaApInvoker(Project project) {
		super(
				project,
				// Scala just uses the java AP configuration afaik. This of course assumes the java AP also gets configured.
				ImmutableList.of(),
				getInvokerTasks(project),
				AnnotationProcessorInvoker.SCALA);
	}

	private static Map<SourceSet, ScalaCompile> getInvokerTasks(Project project) {
		MixinExtension mixin = LoomGradleExtension.get(project).getMixin();
		return mixin.getInvokerTasksStream(AnnotationProcessorInvoker.SCALA)
				.collect(Collectors.toMap(Map.Entry::getKey, entry -> Objects.requireNonNull((ScalaCompile) entry.getValue())));
	}

	@Override
	protected void passArgument(ScalaCompile compileTask, String key, String value) {
		compileTask.getOptions().getCompilerArgs().add("-A" + key + "=" + value);
	}

	@Override
	protected File getRefmapDestinationDir(ScalaCompile task) {
		return task.getDestinationDirectory().get().getAsFile();
	}
}
