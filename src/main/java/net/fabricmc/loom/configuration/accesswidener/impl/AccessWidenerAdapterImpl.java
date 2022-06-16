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

package net.fabricmc.loom.configuration.accesswidener.impl;

import java.nio.file.Path;
import java.util.List;

import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.objectweb.asm.commons.Remapper;

import net.fabricmc.accesswidener.AccessWidener;
import net.fabricmc.accesswidener.AccessWidenerReader;
import net.fabricmc.accesswidener.AccessWidenerRemapper;
import net.fabricmc.accesswidener.AccessWidenerWriter;
import net.fabricmc.accesswidener.TransitiveOnlyFilter;
import net.fabricmc.loom.configuration.accesswidener.AccessWidenerAdapter;
import net.fabricmc.loom.configuration.accesswidener.AccessWidenerProvider;
import net.fabricmc.loom.configuration.accesswidener.ModAccessWidener;
import net.fabricmc.loom.configuration.accesswidener.ValidateAccessWidenerBaseTask;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

public class AccessWidenerAdapterImpl implements AccessWidenerAdapter {
	@Override
	public byte[] remap(byte[] input, Remapper remapper, String from, String to) {
		int version = AccessWidenerReader.readVersion(input);

		AccessWidenerWriter writer = new AccessWidenerWriter(version);
		AccessWidenerRemapper awRemapper = new AccessWidenerRemapper(
				writer,
				remapper,
				from,
				to
		);
		AccessWidenerReader reader = new AccessWidenerReader(awRemapper);
		reader.read(input);
		return writer.write();
	}

	@Override
	public void transformJar(Path jar, List<? extends AccessWidenerProvider> accessWideners) {
		final AccessWidener accessWidener = new AccessWidener();
		final AccessWidenerReader reader = new AccessWidenerReader(accessWidener);

		for (AccessWidenerProvider accessWidenerFile : accessWideners) {
			reader.read(accessWidenerFile.getAccessWidener());
		}

		AccessWidenerTransformer transformer = new AccessWidenerTransformer(accessWidener);
		transformer.apply(jar.toFile());
	}

	@Override
	public void transformJar(Path jar, List<? extends AccessWidenerProvider> accessWideners, Remapper remapper, String from, String to) {
		AccessWidener accessWidener = new AccessWidener();

		AccessWidenerRemapper remappingVisitor = new AccessWidenerRemapper(
				accessWidener,
				remapper,
				from,
				to
		);
		AccessWidenerReader transitiveReader = new AccessWidenerReader(new TransitiveOnlyFilter(remappingVisitor));

		for (AccessWidenerProvider accessWidenerFile : accessWideners) {
			transitiveReader.read(accessWidenerFile.getAccessWidener());
		}

		AccessWidenerTransformer transformer = new AccessWidenerTransformer(accessWidener);
		transformer.apply(jar.toFile());
	}

	@Override
	public void applyMappingComments(List<ModAccessWidener> accessWideners, MemoryMappingTree mappingTree) {
		for (ModAccessWidener classTweaker : accessWideners) {
			MappingCommentVisitor mappingCommentVisitor = new MappingCommentVisitor(classTweaker.modId(), mappingTree);
			AccessWidenerReader accessWidenerReader = new AccessWidenerReader(new TransitiveOnlyFilter(mappingCommentVisitor));
			accessWidenerReader.read(classTweaker.content());
		}
	}

	@Override
	public boolean isTransitive(AccessWidenerProvider accessWidener) {
		return TransitiveDetectorVisitor.isTransitive(accessWidener.getAccessWidener());
	}

	@Override
	public String getNamespace(AccessWidenerProvider accessWidener) {
		return null;
	}

	@Override
	public TaskProvider<? extends ValidateAccessWidenerBaseTask> createValidationTask(TaskContainer tasks) {
		return tasks.register("validateAccessWidener", ValidateAccessWidenerTask.class, t -> {
			t.setDescription("Validate all the rules in the access widener against the Minecraft jar");
			t.setGroup("verification");
		});
	}

	@Override
	public String getName() {
		return "access widener";
	}
}
