/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2026 FabricMC
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

package net.fabricmc.loom.configuration.processors;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.jspecify.annotations.Nullable;

import net.fabricmc.mappingio.FlatMappingVisitor;
import net.fabricmc.mappingio.adapter.RegularAsFlatMappingVisitor;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.VisitableMappingTree;

public final class MappingProcessing {
	public static MappingTree.@Nullable ClassMapping getOrCreateClassMapping(VisitableMappingTree tree, String name, int namespaceId, boolean create) {
		if (create && namespaceId != MappingTree.MIN_NAMESPACE_ID) {
			throw new UnsupportedOperationException("Cannot create entry from dst namespace");
		}

		final MappingTree.ClassMapping existing = tree.getClass(name, namespaceId);

		if (existing != null || !create) {
			return existing;
		}

		final FlatMappingVisitor flat = new RegularAsFlatMappingVisitor(tree);

		try {
			flat.visitContent();
			flat.visitClass(name, (String[]) null);
			flat.visitEnd();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		return tree.getClass(name);
	}

	public static MappingTree.@Nullable FieldMapping getOrCreateFieldMapping(VisitableMappingTree tree, MappingTree.ClassMapping owner, String name, @Nullable String desc, int namespaceId, boolean create) {
		if (create && namespaceId != MappingTree.MIN_NAMESPACE_ID) {
			throw new UnsupportedOperationException("Cannot create entry from dst namespace");
		}

		final MappingTree.FieldMapping existing = owner.getField(name, desc, namespaceId);

		if (existing != null || !create) {
			return existing;
		}

		final FlatMappingVisitor flat = new RegularAsFlatMappingVisitor(tree);

		try {
			flat.visitContent();
			flat.visitField(owner.getSrcName(), name, desc, (String[]) null, null, null);
			flat.visitEnd();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		return owner.getField(name, desc);
	}

	public static MappingTree.@Nullable MethodMapping getOrCreateMethodMapping(VisitableMappingTree tree, MappingTree.ClassMapping owner, String name, @Nullable String desc, int namespaceId, boolean create) {
		if (create && namespaceId != MappingTree.MIN_NAMESPACE_ID) {
			throw new UnsupportedOperationException("Cannot create entry from dst namespace");
		}

		final MappingTree.MethodMapping existing = owner.getMethod(name, desc, namespaceId);

		if (existing != null || !create) {
			return existing;
		}

		final FlatMappingVisitor flat = new RegularAsFlatMappingVisitor(tree);

		try {
			flat.visitContent();
			flat.visitMethod(owner.getSrcName(), name, desc, (String[]) null, null, null);
			flat.visitEnd();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		return owner.getMethod(name, desc);
	}
}
