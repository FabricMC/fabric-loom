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

package net.fabricmc.loom.configuration.providers.mappings.utils;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import net.fabricmc.mappingio.MappedElementKind;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.adapter.ForwardingMappingVisitor;

/**
 * Adds the constructor method {@code <init>} to the destination namespaces,
 * as long as the source name is {@code <init>} and the destination mapping is null.
 */
public class AddConstructorMappingVisitor extends ForwardingMappingVisitor {
	private boolean inConstructor;
	private boolean[] namespaceVisited;

	public AddConstructorMappingVisitor(MappingVisitor next) {
		super(next);
	}

	@Override
	public void visitNamespaces(String srcNamespace, List<String> dstNamespaces) throws IOException {
		namespaceVisited = new boolean[dstNamespaces.size()];
		super.visitNamespaces(srcNamespace, dstNamespaces);
	}

	@Override
	public boolean visitMethod(String srcName, String srcDesc) throws IOException {
		if ("<init>".equals(srcName)) {
			inConstructor = true;
			Arrays.fill(namespaceVisited, false);
		} else {
			inConstructor = false;
		}

		return super.visitMethod(srcName, srcDesc);
	}

	@Override
	public boolean visitElementContent(MappedElementKind targetKind) throws IOException {
		if (inConstructor) {
			inConstructor = false;

			for (int i = 0; i < namespaceVisited.length; i++) {
				if (!namespaceVisited[i]) {
					visitDstName(targetKind, i, "<init>");
				}
			}
		}

		return super.visitElementContent(targetKind);
	}

	@Override
	public void visitDstName(MappedElementKind targetKind, int namespace, String name) throws IOException {
		namespaceVisited[namespace] = true;
		super.visitDstName(targetKind, namespace, name);
	}
}
