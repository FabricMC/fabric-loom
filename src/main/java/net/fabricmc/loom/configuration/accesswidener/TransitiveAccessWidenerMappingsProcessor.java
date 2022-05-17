/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021-2022 FabricMC
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

import java.util.List;

import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

import net.fabricmc.accesswidener.AccessWidenerReader;
import net.fabricmc.accesswidener.AccessWidenerVisitor;
import net.fabricmc.accesswidener.TransitiveOnlyFilter;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.task.GenerateSourcesTask;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

public record TransitiveAccessWidenerMappingsProcessor(Project project) implements GenerateSourcesTask.MappingsProcessor {
	@Override
	public boolean transform(MemoryMappingTree mappings) {
		final LoomGradleExtension extension = LoomGradleExtension.get(project);
		List<AccessWidenerFile> accessWideners = extension.getTransitiveAccessWideners();

		if (accessWideners.isEmpty()) {
			return false;
		}

		if (!MappingsNamespace.INTERMEDIARY.toString().equals(mappings.getSrcNamespace())) {
			throw new IllegalStateException("Mapping tree must have intermediary src mappings not " + mappings.getSrcNamespace());
		}

		for (AccessWidenerFile accessWidener : accessWideners) {
			MappingCommentVisitor mappingCommentVisitor = new MappingCommentVisitor(accessWidener.modId(), mappings, project.getLogger());
			AccessWidenerReader accessWidenerReader = new AccessWidenerReader(new TransitiveOnlyFilter(mappingCommentVisitor));
			accessWidenerReader.read(accessWidener.content());
		}

		return true;
	}

	private record MappingCommentVisitor(String modId, MemoryMappingTree mappingTree, Logger logger) implements AccessWidenerVisitor {
		@Override
		public void visitClass(String name, AccessWidenerReader.AccessType access, boolean transitive) {
			MappingTree.ClassMapping classMapping = mappingTree.getClass(name);

			if (classMapping == null) {
				logger.info("Failed to find class ({}) to mark access widened by mod ({})", name, modId());
				return;
			}

			classMapping.setComment(appendComment(classMapping.getComment(), access));
		}

		@Override
		public void visitMethod(String owner, String name, String descriptor, AccessWidenerReader.AccessType access, boolean transitive) {
			// Access is also applied to the class, so also add the comment to the class
			visitClass(owner, access, transitive);

			MappingTree.ClassMapping classMapping = mappingTree.getClass(owner);

			if (classMapping == null) {
				logger.info("Failed to find class ({}) to mark access widened by mod ({})", owner, modId());
				return;
			}

			MappingTree.MethodMapping methodMapping = classMapping.getMethod(name, descriptor);

			if (methodMapping == null) {
				logger.info("Failed to find method ({}) in ({}) to mark access widened by mod ({})", name, owner, modId());
				return;
			}

			methodMapping.setComment(appendComment(methodMapping.getComment(), access));
		}

		@Override
		public void visitField(String owner, String name, String descriptor, AccessWidenerReader.AccessType access, boolean transitive) {
			// Access is also applied to the class, so also add the comment to the class
			visitClass(owner, access, transitive);

			MappingTree.ClassMapping classMapping = mappingTree.getClass(owner);

			if (classMapping == null) {
				logger.info("Failed to find class ({}) to mark access widened by mod ({})", name, modId());
				return;
			}

			MappingTree.FieldMapping fieldMapping = classMapping.getField(name, descriptor);

			if (fieldMapping == null) {
				logger.info("Failed to find field ({}) in ({}) to mark access widened by mod ({})", name, owner, modId());
				return;
			}

			fieldMapping.setComment(appendComment(fieldMapping.getComment(), access));
		}

		private String appendComment(String comment, AccessWidenerReader.AccessType access) {
			if (comment == null) {
				comment = "";
			} else {
				comment += "\n";
			}

			String awComment = "Access widened by %s to %s".formatted(modId(), access);

			if (!comment.contains(awComment)) {
				// Ensure we don't comment the same thing twice. A bit of a cheap way to do this, but should work ok.
				comment += awComment;
			}

			return comment;
		}
	}
}
