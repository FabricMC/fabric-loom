/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022 FabricMC
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

package net.fabricmc.loom.configuration.classtweaker;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import net.fabricmc.classtweaker.api.ClassTweaker;
import net.fabricmc.classtweaker.api.ClassTweakerReader;
import net.fabricmc.classtweaker.api.visitor.ClassTweakerVisitor;

final class ClassTweakerFactoryImpl implements ClassTweakerFactory {
	@Override
	public int readVersion(byte[] data) {
		Objects.requireNonNull(data);
		return ClassTweakerReader.readVersion(data);
	}

	@Override
	public void read(ClassTweakerVisitor visitor, byte[] data, String modId) {
		Objects.requireNonNull(visitor);
		Objects.requireNonNull(data);
		Objects.requireNonNull(modId);

		ClassTweakerReader.create(visitor).read(data, modId);
	}

	@Override
	public ClassTweaker readEntries(List<ClassTweakerEntry> entries) throws IOException {
		final ClassTweaker classTweaker = ClassTweaker.newInstance();

		for (ClassTweakerEntry entry : entries) {
			ClassTweakerVisitor visitor = classTweaker;

			if (!entry.local()) {
				visitor = ClassTweakerVisitor.transitiveOnly(visitor);
			}

			entry.read(this, visitor);
		}

		return classTweaker;
	}

	@Override
	public void transformJar(Path jar, ClassTweaker classTweaker) throws IOException {
		new ClassTweakerJarTransformer(classTweaker).transform(jar);
	}
}
