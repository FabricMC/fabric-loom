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

package net.fabricmc.loom.api.processors;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import org.gradle.api.Project;
import org.objectweb.asm.commons.Remapper;

import net.fabricmc.loom.task.RemapJarTask;

public class JarProcessorManager {
	private final Project project;
	private final List<JarProcessor> processors;
	private final Path work;

	public JarProcessorManager(Project project, List<JarProcessor> processors, Path work) {
		this.project = project;
		this.processors = processors;
		this.work = work;
	}

	public boolean process(Path input, Path output) throws IOException {
		if (processors.isEmpty()) {
			return false;
		}

		if (!Files.exists(output) || !processors.stream().allMatch(processor -> processor.isUpToDate(project, output))) {
			project.getLogger().lifecycle(":processing mapped jar");
			Path in = input;

			for (int processorCount = processors.size(), i = 0; i < processorCount; i++) {
				project.getLogger().lifecycle(":processing mapped jar [" + (i + 1) + " of " + processorCount + "]");
				Path out = work.resolve("work-" + i + ".jar");
				Files.deleteIfExists(out);

				if (!processors.get(i).processInput(project, in, out)) {
					in = out;
				}
			}

			Files.createDirectories(output.getParent());
			Files.copy(in, output, StandardCopyOption.REPLACE_EXISTING);
		}

		return true;
	}

	public void processRemapped(RemapJarTask task, Remapper remapper, Path jar) throws IOException {
		for (JarProcessor processor : processors) {
			processor.processRemapped(project, task, remapper, jar);
		}
	}
}
