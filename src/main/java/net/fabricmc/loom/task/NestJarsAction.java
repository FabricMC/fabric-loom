/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2021 FabricMC
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

package net.fabricmc.loom.task;

import java.io.File;
import java.io.Serializable;
import java.util.Arrays;

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.Provider;
import org.gradle.jvm.tasks.Jar;
import org.jetbrains.annotations.NotNull;

import net.fabricmc.loom.build.nesting.JarNester;

/**
 * Configuration-cache-compatible action for nesting jars.
 * Uses a provider to avoid capturing task references at configuration time.
 * Do NOT turn me into a record!
 */
class NestJarsAction implements Action<Task>, Serializable {
	private final Provider<Directory> nestedJarsDir;

	NestJarsAction(Provider<Directory> nestedJarsDir) {
		this.nestedJarsDir = nestedJarsDir;
	}

	@Override
	public void execute(@NotNull Task t) {
		final Jar jarTask = (Jar) t;
		final File jarFile = jarTask.getArchiveFile().get().getAsFile();

		if (!nestedJarsDir.isPresent()) {
			return;
		}

		final File outputDir = nestedJarsDir.get().getAsFile();

		if (outputDir.exists() && outputDir.isDirectory()) {
			final File[] jars = outputDir.listFiles((dir, name) -> name.endsWith(".jar"));

			if (jars != null && jars.length > 0) {
				JarNester.nestJars(
						Arrays.asList(jars),
						jarFile,
						jarTask.getLogger()
				);
				jarTask.getLogger().lifecycle("Nested {} jar(s) into {}", jars.length, jarFile.getName());
			}
		}
	}
}