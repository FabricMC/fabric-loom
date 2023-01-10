/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021-2023 FabricMC
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.accesswidener.AccessWidenerReader;
import net.fabricmc.accesswidener.AccessWidenerVisitor;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.api.processor.MappingProcessorContext;
import net.fabricmc.loom.api.processor.MinecraftJarProcessor;
import net.fabricmc.loom.util.LazyCloseable;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.tinyremapper.TinyRemapper;

public final class TransitiveAccessWidenerMappingsProcessor implements MinecraftJarProcessor.MappingsProcessor<AccessWidenerJarProcessor.Spec> {
	public static final TransitiveAccessWidenerMappingsProcessor INSTANCE = new TransitiveAccessWidenerMappingsProcessor();

	private TransitiveAccessWidenerMappingsProcessor() {
	}

	@Override
	public boolean transform(MemoryMappingTree mappings, AccessWidenerJarProcessor.Spec spec, MappingProcessorContext context) {
		final List<AccessWidenerEntry> accessWideners = spec.accessWideners().stream()
				.filter(entry -> entry.mappingId() != null)
				.toList();

		if (accessWideners.isEmpty()) {
			return false;
		}

		if (!MappingsNamespace.INTERMEDIARY.toString().equals(mappings.getSrcNamespace())) {
			throw new IllegalStateException("Mapping tree must have intermediary src mappings not " + mappings.getSrcNamespace());
		}

		try (LazyCloseable<TinyRemapper> remapper = context.createRemapper(MappingsNamespace.INTERMEDIARY, MappingsNamespace.NAMED)) {
			for (AccessWidenerEntry accessWidener : accessWideners) {
				var visitor = new MappingCommentVisitor(accessWidener.mappingId(), mappings);
				accessWidener.read(visitor, remapper);
			}
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to transform access widener mappings", e);
		}

		return true;
	}

	private record MappingCommentVisitor(String modId, MemoryMappingTree mappingTree) implements AccessWidenerVisitor {
		private static final Logger LOGGER = LoggerFactory.getLogger(MappingCommentVisitor.class);

		@Override
		public void visitClass(String name, AccessWidenerReader.AccessType access, boolean transitive) {
			MappingTree.ClassMapping classMapping = mappingTree.getClass(name);

			if (classMapping == null) {
				LOGGER.info("Failed to find class ({}) to mark access widened by mod ({})", name, modId());
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
				LOGGER.info("Failed to find class ({}) to mark access widened by mod ({})", owner, modId());
				return;
			}

			MappingTree.MethodMapping methodMapping = classMapping.getMethod(name, descriptor);

			if (methodMapping == null) {
				LOGGER.info("Failed to find method ({}) in ({}) to mark access widened by mod ({})", name, owner, modId());
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
				LOGGER.info("Failed to find class ({}) to mark access widened by mod ({})", name, modId());
				return;
			}

			MappingTree.FieldMapping fieldMapping = classMapping.getField(name, descriptor);

			if (fieldMapping == null) {
				LOGGER.info("Failed to find field ({}) in ({}) to mark access widened by mod ({})", name, owner, modId());
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
