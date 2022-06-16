/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2020-2021 FabricMC
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

package net.fabricmc.loom.configuration.accesswidener;

import java.nio.file.Path;
import java.util.List;

import org.gradle.api.Project;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.objectweb.asm.commons.Remapper;

import net.fabricmc.mappingio.tree.MemoryMappingTree;

public interface AccessWidenerAdapter {
	static AccessWidenerAdapter get(Project project) {
		return AccessWidenerAdapterService.get(project);
	}

	byte[] remap(byte[] input, Remapper remapper, String from, String to);

	void transformJar(Path jar, List<? extends AccessWidenerProvider> accessWideners);

	void transformJar(Path jar, List<? extends AccessWidenerProvider> accessWideners, Remapper remapper, String from, String to);

	void applyMappingComments(List<ModAccessWidener> accessWideners, MemoryMappingTree mappingTree);

	boolean isTransitive(AccessWidenerProvider accessWidener);

	String getNamespace(AccessWidenerProvider accessWidener);

	// TODO may need further abstraction, as the classloader boundary might fuckup with gradle classes
	TaskProvider<? extends ValidateAccessWidenerBaseTask> createValidationTask(TaskContainer tasks);

	String getName();
}
