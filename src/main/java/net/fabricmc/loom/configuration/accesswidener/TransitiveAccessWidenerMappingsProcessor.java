/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021-2026 FabricMC
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

import net.fabricmc.classtweaker.api.visitor.AccessWidenerVisitor;
import net.fabricmc.classtweaker.api.visitor.ClassTweakerVisitor;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.api.processor.MappingProcessorContext;
import net.fabricmc.loom.api.processor.MinecraftJarProcessor;
import net.fabricmc.loom.configuration.processors.MappingProcessing;
import net.fabricmc.loom.util.LazyCloseable;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MappingTreeView;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.tinyremapper.TinyRemapper;

public final class TransitiveAccessWidenerMappingsProcessor implements MinecraftJarProcessor.MappingsProcessor<AccessWidenerJarProcessor.Spec> {
	public static final TransitiveAccessWidenerMappingsProcessor INSTANCE = new TransitiveAccessWidenerMappingsProcessor();

	private static final Logger LOGGER = LoggerFactory.getLogger(TransitiveAccessWidenerMappingsProcessor.class);

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

		int productionNamespaceId = mappings.getNamespaceId(context.getProductionNamespace().toString());

		if (productionNamespaceId == MappingTreeView.NULL_NAMESPACE_ID) {
			throw new IllegalStateException("Mapping tree must have namespace %s".formatted(context.getProductionNamespace().toString()));
		}

		try {
			if (!context.disableObfuscation()) {
				try (LazyCloseable<TinyRemapper> remapper = context.createRemapper(context.getProductionNamespace(), MappingsNamespace.NAMED)) {
					for (AccessWidenerEntry accessWidener : accessWideners) {
						var visitor = new MappingCommentClassTweakerVisitor(accessWidener.mappingId(), productionNamespaceId, mappings, false);
						accessWidener.read(visitor, remapper, context.getProductionNamespace());
					}
				}
			} else {
				for (AccessWidenerEntry accessWidener : accessWideners) {
					var visitor = new MappingCommentClassTweakerVisitor(accessWidener.mappingId(), productionNamespaceId, mappings, true);
					accessWidener.readOfficial(visitor);
				}
			}
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to transform access widener mappings", e);
		}

		return true;
	}

	private record MappingCommentClassTweakerVisitor(String modId, int productionNamespaceId, MemoryMappingTree mappingTree, boolean createMissingEntries) implements ClassTweakerVisitor {
		@Override
		public AccessWidenerVisitor visitAccessWidener(String owner) {
			return new MappingCommentAccessWidenerVisitor(owner);
		}

		private class MappingCommentAccessWidenerVisitor implements AccessWidenerVisitor {
			private final String className;

			private MappingCommentAccessWidenerVisitor(String className) {
				this.className = className;
			}

			@Override
			public void visitClass(AccessType access, boolean transitive) {
				MappingTree.ClassMapping classMapping = MappingProcessing.getOrCreateClassMapping(mappingTree, className, productionNamespaceId(), createMissingEntries);

				if (classMapping == null) {
					LOGGER.info("Failed to find class ({}) to mark access widened by mod ({})", className, modId());
					return;
				}

				classMapping.setComment(appendComment(classMapping.getComment(), access));
			}

			@Override
			public void visitMethod(String name, String descriptor, AccessType access, boolean transitive) {
				// Access is also applied to the class, so also add the comment to the class
				visitClass(access, transitive);

				MappingTree.ClassMapping classMapping = MappingProcessing.getOrCreateClassMapping(mappingTree, className, productionNamespaceId(), createMissingEntries);

				if (classMapping == null) {
					LOGGER.info("Failed to find class ({}) to mark access widened by mod ({})", className, modId());
					return;
				}

				MappingTree.MethodMapping methodMapping = MappingProcessing.getOrCreateMethodMapping(mappingTree, classMapping, name, descriptor, productionNamespaceId(), createMissingEntries);

				if (methodMapping == null) {
					LOGGER.info("Failed to find method ({}) in ({}) to mark access widened by mod ({})", name, className, modId());
					return;
				}

				methodMapping.setComment(appendComment(methodMapping.getComment(), access));
			}

			@Override
			public void visitField(String name, String descriptor, AccessType access, boolean transitive) {
				// Access is also applied to the class, so also add the comment to the class
				visitClass(access, transitive);

				MappingTree.ClassMapping classMapping = MappingProcessing.getOrCreateClassMapping(mappingTree, className, productionNamespaceId(), createMissingEntries);

				if (classMapping == null) {
					LOGGER.info("Failed to find class ({}) to mark access widened by mod ({})", name, modId());
					return;
				}

				MappingTree.FieldMapping fieldMapping = MappingProcessing.getOrCreateFieldMapping(mappingTree, classMapping, name, descriptor, productionNamespaceId(), createMissingEntries);

				if (fieldMapping == null) {
					LOGGER.info("Failed to find field ({}) in ({}) to mark access widened by mod ({})", name, className, modId());
					return;
				}

				fieldMapping.setComment(appendComment(fieldMapping.getComment(), access));
			}

			private String appendComment(String comment, AccessType access) {
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
}
