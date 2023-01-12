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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.VisibleForTesting;

import net.fabricmc.classtweaker.api.ClassTweaker;
import net.fabricmc.classtweaker.api.visitor.ClassTweakerVisitor;
import net.fabricmc.loom.util.fmj.FabricModJson;
import net.fabricmc.loom.util.fmj.ModEnvironment;

public record ClassTweakerEntry(FabricModJson modJson, String path, ModEnvironment environment, boolean local) {
	public static List<ClassTweakerEntry> readAll(FabricModJson modJson, boolean local) {
		var entries = new ArrayList<ClassTweakerEntry>();

		for (Map.Entry<String, ModEnvironment> entry : modJson.getClassTweakers().entrySet()) {
			entries.add(new ClassTweakerEntry(modJson, entry.getKey(), entry.getValue(), local));
		}

		return Collections.unmodifiableList(entries);
	}

	@VisibleForTesting
	public int maxSupportedVersion() {
		if (modJson.getMetadataVersion() >= 2) {
			// FMJ 2.0 is required for class tweaker support. Otherwise limited to access wideners.
			return ClassTweaker.CT_V1;
		}

		return ClassTweaker.AW_V2;
	}

	public void read(ClassTweakerFactory factory, ClassTweakerVisitor classTweakerVisitor) throws IOException {
		final byte[] data = readRaw();
		final int ctVersion = factory.readVersion(data);

		if (ctVersion > maxSupportedVersion()) {
			throw new UnsupportedOperationException("Class tweaker '%s' with version '%d' from mod '%s' is not supported with by this metadata version '%d'.".formatted(path, ctVersion, modJson.getId(), modJson().getMetadataVersion()));
		}

		factory.read(classTweakerVisitor, data, modJson.getId());
	}

	private byte[] readRaw() throws IOException {
		return modJson.getSource().read(path);
	}
}
