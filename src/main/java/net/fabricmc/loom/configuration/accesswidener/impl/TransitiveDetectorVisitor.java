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

import net.fabricmc.accesswidener.AccessWidenerReader;
import net.fabricmc.accesswidener.AccessWidenerVisitor;

public class TransitiveDetectorVisitor implements AccessWidenerVisitor {
	private boolean transitive = false;

	@Override
	public void visitClass(String name, AccessWidenerReader.AccessType access, boolean transitive) {
		if (transitive) {
			this.transitive = true;
		}
	}

	@Override
	public void visitMethod(String owner, String name, String descriptor, AccessWidenerReader.AccessType access, boolean transitive) {
		if (transitive) {
			this.transitive = true;
		}
	}

	@Override
	public void visitField(String owner, String name, String descriptor, AccessWidenerReader.AccessType access, boolean transitive) {
		if (transitive) {
			this.transitive = true;
		}
	}

	public static boolean isTransitive(byte[] content) {
		if (AccessWidenerReader.readVersion(content) < 2) {
			// Transitive AWs are only in v2 or higher, so we can save parsing the file to find out...
			return false;
		}

		TransitiveDetectorVisitor transitiveDetector = new TransitiveDetectorVisitor();
		new AccessWidenerReader(transitiveDetector).read(content);
		return transitiveDetector.transitive;
	}
}
