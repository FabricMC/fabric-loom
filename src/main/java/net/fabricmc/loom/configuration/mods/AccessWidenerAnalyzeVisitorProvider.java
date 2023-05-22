/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2023 FabricMC
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

package net.fabricmc.loom.configuration.mods;

import java.io.IOException;
import java.util.List;

import org.objectweb.asm.ClassVisitor;

import net.fabricmc.accesswidener.AccessWidener;
import net.fabricmc.accesswidener.AccessWidenerClassVisitor;
import net.fabricmc.accesswidener.AccessWidenerReader;
import net.fabricmc.loom.configuration.mods.dependency.ModDependency;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.tinyremapper.TinyRemapper;

public record AccessWidenerAnalyzeVisitorProvider(AccessWidener accessWidener) implements TinyRemapper.AnalyzeVisitorProvider {
	static AccessWidenerAnalyzeVisitorProvider createFromMods(String namespace, List<ModDependency> mods) throws IOException {
		AccessWidener accessWidener = new AccessWidener();
		accessWidener.visitHeader(namespace);

		for (ModDependency mod : mods) {
			final var accessWidenerData = AccessWidenerUtils.readAccessWidenerData(mod.getInputFile());

			if (accessWidenerData == null) {
				continue;
			}

			final var reader = new AccessWidenerReader(accessWidener);
			reader.read(accessWidenerData.content());
		}

		return new AccessWidenerAnalyzeVisitorProvider(accessWidener);
	}

	@Override
	public ClassVisitor insertAnalyzeVisitor(int mrjVersion, String className, ClassVisitor next) {
		return AccessWidenerClassVisitor.createClassVisitor(Constants.ASM_VERSION, next, accessWidener);
	}
}
