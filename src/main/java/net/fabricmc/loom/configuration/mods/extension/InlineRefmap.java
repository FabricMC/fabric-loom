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

package net.fabricmc.loom.configuration.mods.extension;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Predicate;

import net.fabricmc.loom.configuration.mods.ArtifactMetadata;
import net.fabricmc.loom.configuration.mods.dependency.ModDependency;
import net.fabricmc.loom.configuration.mods.dependency.refmap.MixinReferenceRemapper;
import net.fabricmc.loom.configuration.mods.dependency.refmap.MixinRefmapInliner;
import net.fabricmc.loom.configuration.mods.dependency.refmap.MixinRefmapInlinerApplyVisitorProvider;
import net.fabricmc.tinyremapper.InputTag;
import net.fabricmc.tinyremapper.TinyRemapper;

final class InlineRefmap implements ModProcessorExtension {
	static final InlineRefmap INSTANCE = new InlineRefmap();

	private InlineRefmap() {
	}

	@Override
	public boolean appliesTo(ModDependency modDependency) {
		return modDependency.getOptions().getInlineRefmap().get()
				&& modDependency.getMetadata().mixinRemapType() == ArtifactMetadata.MixinRemapType.MIXIN;
	}

	@Override
	public TinyRemapper.Extension createExtension(Context ctx, Predicate<InputTag> applyPredicate) throws IOException {
		MixinReferenceRemapper refmapRemapper = MixinRefmapInliner.createRemapper(ctx.from(), ctx.to(), ctx.mods());
		return new MixinRefmapInlinerApplyVisitorProvider(refmapRemapper, applyPredicate);
	}

	@Override
	public void finalise(ModDependency modDependency, Path path) throws IOException {
	}
}
