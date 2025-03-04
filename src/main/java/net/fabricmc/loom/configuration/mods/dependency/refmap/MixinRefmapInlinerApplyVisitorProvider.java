/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 FabricMC
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

package net.fabricmc.loom.configuration.mods.dependency.refmap;

import java.util.function.Predicate;

import org.objectweb.asm.ClassVisitor;

import net.fabricmc.tinyremapper.InputTag;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.api.TrClass;

public record MixinRefmapInlinerApplyVisitorProvider(
		MixinReferenceRemapper remapper,
		// A set of input tags that do NOT need their refmaps inlined
		Predicate<InputTag> staticRemappedMixins) implements TinyRemapper.ApplyVisitorProvider, TinyRemapper.Extension {
	@Override
	public ClassVisitor insertApplyVisitor(TrClass cls, ClassVisitor next) {
		return new MixinRefmapInlinerClassVisitor(remapper, next);
	}

	@Override
	public ClassVisitor insertApplyVisitor(TrClass cls, ClassVisitor next, InputTag[] inputTags) {
		for (InputTag tag : inputTags) {
			if (staticRemappedMixins.test(tag)) {
				// No need to inline the refmaps for this tag, as we know this was originally a statically remapped mixin with no refmap
				return next;
			}
		}

		return new MixinRefmapInlinerClassVisitor(remapper, next);
	}

	@Override
	public void attach(TinyRemapper.Builder builder) {
		builder.extraPreApplyVisitor(this);
	}
}
