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
import java.nio.file.Path;

import org.gradle.api.Project;
import org.objectweb.asm.commons.Remapper;

import net.fabricmc.loom.task.RemapJarTask;

public interface JarProcessor {
	/**
	 * Checks if the processed file is up to date.
	 * @param project The project
	 * @param path The jar which was processed last
	 * @return Is this processor up to date with this (potentially unprocessed) jar
	 */
	boolean isUpToDate(Project project, Path path);

	/**
	 * Process the Minecraft jar.
	 * @param project The project
	 * @param from The jar. Can be the original Minecraft jar or the output of the previous processor
	 * @param to The destination to write the jar to
	 * @return Should this processor be skipped
	 * @throws IOException If any {@link IOException exceptions} happen while processing
	 */
	boolean processInput(Project project, Path from, Path to) throws IOException;

	/**
	 * Processes the jar produced by {@link net.fabricmc.loom.task.RemapJarTask}.
	 * @param project The project
	 * @param remapper The ASM remapper being used
	 * @param jar The jar to (optionally) process
	 */
	void processRemapped(Project project, RemapJarTask task, Remapper remapper, Path jar) throws IOException;
}
