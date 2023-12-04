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

package net.fabricmc.loom.extension;

import javax.inject.Inject;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.commons.Remapper;

import net.fabricmc.loom.api.remapping.RemapperContext;
import net.fabricmc.loom.api.remapping.RemapperExtension;
import net.fabricmc.loom.api.remapping.RemapperParameters;
import net.fabricmc.loom.api.remapping.TinyRemapperExtension;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.api.TrClass;

public abstract class RemapperExtensionHolder {
	private final RemapperParameters remapperParameters;

	@Inject
	public RemapperExtensionHolder(RemapperParameters remapperParameters) {
		this.remapperParameters = remapperParameters;
	}

	@Input
	public abstract Property<String> getRemapperExtensionClassName();

	@Nested
	public RemapperParameters getRemapperParameters() {
		return remapperParameters;
	}

	public void apply(TinyRemapper.Builder tinyRemapperBuilder, String sourceNamespace, String targetNamespace, ObjectFactory objectFactory) {
		final RemapperExtension<?> remapperExtension = newInstance(objectFactory);

		tinyRemapperBuilder.extraPostApplyVisitor(new RemapperExtensionImpl(remapperExtension, sourceNamespace, targetNamespace));

		if (remapperExtension instanceof TinyRemapperExtension tinyRemapperExtension) {
			if (tinyRemapperExtension.getAnalyzeVisitorProvider() != null) {
				tinyRemapperBuilder.extraAnalyzeVisitor(tinyRemapperExtension.getAnalyzeVisitorProvider());
			}

			if (tinyRemapperExtension.getPreApplyVisitor() != null) {
				tinyRemapperBuilder.extraPreApplyVisitor(tinyRemapperExtension.getPreApplyVisitor());
			}

			if (tinyRemapperExtension.getPostApplyVisitor() != null) {
				tinyRemapperBuilder.extraPostApplyVisitor(tinyRemapperExtension.getPostApplyVisitor());
			}
		}
	}

	private RemapperExtension<?> newInstance(ObjectFactory objectFactory) {
		try {
			Class<? extends RemapperExtension> remapperExtensionClass = Class.forName(getRemapperExtensionClassName().get())
					.asSubclass(RemapperExtension.class);
			return objectFactory.newInstance(remapperExtensionClass, getRemapperParameters());
		} catch (Exception e) {
			throw new RuntimeException("Failed to create remapper extension", e);
		}
	}

	private static final class RemapperExtensionImpl implements TinyRemapper.ApplyVisitorProvider {
		private final RemapperExtension<?> remapperExtension;
		private final String sourceNamespace;
		private final String targetNamespace;

		@Nullable
		private RemapperContext context;

		private RemapperExtensionImpl(RemapperExtension<?> remapperExtension, String sourceNamespace, String targetNamespace) {
			this.remapperExtension = remapperExtension;
			this.sourceNamespace = sourceNamespace;
			this.targetNamespace = targetNamespace;
		}

		@Override
		public ClassVisitor insertApplyVisitor(TrClass cls, ClassVisitor next) {
			if (context == null) {
				context = new RemapperContextImpl(cls.getEnvironment().getRemapper(), sourceNamespace, targetNamespace);
			}

			return remapperExtension.insertVisitor(cls.getName(), context, next);
		}
	}

	private record RemapperContextImpl(Remapper remapper, String sourceNamespace, String targetNamespace) implements RemapperContext {
	}
}
