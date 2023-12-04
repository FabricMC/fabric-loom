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

import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.commons.Remapper;

import net.fabricmc.loom.api.remapping.RemapperContext;
import net.fabricmc.loom.api.remapping.RemapperExtension;
import net.fabricmc.loom.api.remapping.RemapperParameters;
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

	public void apply(TinyRemapper.Builder tinyRemapperBuilder) {
		RemapperExtension<?> remapperExtension = newInstance();

		tinyRemapperBuilder.extraPostApplyVisitor(new RemapperExtensionImpl(remapperExtension));

		if (remapperExtension instanceof TinyRemapper.AnalyzeVisitorProvider analyzeVisitorProvider) {
			tinyRemapperBuilder.extraAnalyzeVisitor(analyzeVisitorProvider);
		}

		if (remapperExtension instanceof TinyRemapper.ApplyVisitorProvider applyVisitorProvider) {
			// TODO allow having a pre apply visitor?
			tinyRemapperBuilder.extraPostApplyVisitor(applyVisitorProvider);
		}
	}

	private RemapperExtension<?> newInstance() {
		try {
			return Class.forName(getRemapperExtensionClassName().get())
					.asSubclass(RemapperExtension.class)
					.getConstructor()
					.newInstance();
		} catch (Exception e) {
			throw new RuntimeException("Failed to create remapper extension", e);
		}
	}

	private record RemapperExtensionImpl(RemapperExtension<?> remapperExtension) implements TinyRemapper.ApplyVisitorProvider {
		@Override
		public ClassVisitor insertApplyVisitor(TrClass cls, ClassVisitor next) {
			return remapperExtension.insertVisitor(cls.getName(), new RemapperContext() {
				// TODO dont create a new instance of this for every class
				@Override
				public Remapper getRemapper() {
					return cls.getEnvironment().getRemapper();
				}

				@Override
				public String sourceNamespace() {
					return null;
				}

				@Override
				public String targetNamespace() {
					return null;
				}
			}, next);
		}
	}
}
