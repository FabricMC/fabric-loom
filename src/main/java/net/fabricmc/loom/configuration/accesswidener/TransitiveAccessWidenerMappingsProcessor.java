/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021 FabricMC
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
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.gradle.api.logging.Logger;

import net.fabricmc.accesswidener.AccessWidenerReader;
import net.fabricmc.accesswidener.AccessWidenerVisitor;
import net.fabricmc.accesswidener.TransitiveOnlyFilter;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.Tiny2Writer;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

public final class TransitiveAccessWidenerMappingsProcessor {
	private TransitiveAccessWidenerMappingsProcessor() {
	}

	public static void process(Path inputMappings, Path outputMappings, List<AccessWidenerFile> accessWideners, Logger logger) {
		MemoryMappingTree mappingTree = new MemoryMappingTree();

		try (Reader reader = Files.newBufferedReader(inputMappings, StandardCharsets.UTF_8)) {
			MappingReader.read(reader, new MappingSourceNsSwitch(mappingTree, MappingsNamespace.INTERMEDIARY.toString()));
		} catch (IOException e) {
			throw new RuntimeException("Failed to read mappings", e);
		}

		if (!MappingsNamespace.INTERMEDIARY.toString().equals(mappingTree.getSrcNamespace())) {
			throw new IllegalStateException("Mapping tree must have intermediary src mappings not " + mappingTree.getSrcNamespace());
		}

		for (AccessWidenerFile accessWidener : accessWideners) {
			MappingCommentVisitor mappingCommentVisitor = new MappingCommentVisitor(accessWidener.modId(), mappingTree, logger);
			AccessWidenerReader accessWidenerReader = new AccessWidenerReader(new TransitiveOnlyFilter(mappingCommentVisitor));
			accessWidenerReader.read(accessWidener.content());
		}

		try (Writer writer = Files.newBufferedWriter(outputMappings, StandardCharsets.UTF_8)) {
			Tiny2Writer tiny2Writer = new Tiny2Writer(writer, false);
			mappingTree.accept(new MappingSourceNsSwitch(tiny2Writer, MappingsNamespace.NAMED.toString()));
		} catch (IOException e) {
			throw new RuntimeException("Failed to write mappings", e);
		}
	}

	private static record MappingCommentVisitor(String modId, MemoryMappingTree mappingTree, Logger logger) implements AccessWidenerVisitor {
		@Override
		public void visitClass(String name, AccessWidenerReader.AccessType access, boolean transitive) {
			MappingTree.ClassMapping classMapping = mappingTree.getClass(name);

			if (classMapping == null) {
				logger.warn("Failed to find class ({}) to mark access widened by mod ({})", name, modId());
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
				logger.warn("Failed to find class ({}) to mark access widened by mod ({})", owner, modId());
				return;
			}

			MappingTree.MethodMapping methodMapping = classMapping.getMethod(name, descriptor);

			if (methodMapping == null) {
				logger.warn("Failed to find method ({}) in ({}) to mark access widened by mod ({})", name, owner, modId());
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
				logger.warn("Failed to find class ({}) to mark access widened by mod ({})", name, modId());
				return;
			}

			MappingTree.FieldMapping fieldMapping = classMapping.getField(name, descriptor);

			if (fieldMapping == null) {
				logger.warn("Failed to find field ({}) in ({}) to mark access widened by mod ({})", name, owner, modId());
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

			comment += "Access widened by %s to %s".formatted(modId(), access);

			return comment;
		}
	}
}
