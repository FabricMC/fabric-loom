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

package net.fabricmc.loom.configuration.providers.minecraft;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.gradle.api.Project;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.commons.Remapper;

import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.providers.mappings.MappingConfiguration;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.TinyRemapperHelper;
import net.fabricmc.loom.util.service.SharedServiceManager;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.api.TrClass;

public record SignatureFixerApplyVisitor(Map<String, String> signatureFixes) implements TinyRemapper.ApplyVisitorProvider {
	@Override
	public ClassVisitor insertApplyVisitor(TrClass cls, ClassVisitor next) {
		return new ClassVisitor(Constants.ASM_VERSION, next) {
			@Override
			public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
				if (signature == null) {
					signature = signatureFixes.getOrDefault(name, null);
				}

				super.visit(version, access, name, signature, superName, interfaces);
			}
		};
	}

	public static Map<String, String> getRemappedSignatures(boolean toIntermediary, MappingConfiguration mappingConfiguration, Project project, SharedServiceManager serviceManager, String targetNamespace) throws IOException {
		if (mappingConfiguration.getSignatureFixes() == null) {
			// No fixes
			return Collections.emptyMap();
		}

		if (toIntermediary) {
			// No need to remap, as these are already intermediary
			return mappingConfiguration.getSignatureFixes();
		}

		// Remap the sig fixes from intermediary to the target namespace
		final Map<String, String> remapped = new HashMap<>();
		final TinyRemapper sigTinyRemapper = TinyRemapperHelper.getTinyRemapper(project, serviceManager, MappingsNamespace.INTERMEDIARY.toString(), targetNamespace);
		final Remapper sigAsmRemapper = sigTinyRemapper.getEnvironment().getRemapper();

		// Remap the class names and the signatures using a new tiny remapper instance.
		for (Map.Entry<String, String> entry : mappingConfiguration.getSignatureFixes().entrySet()) {
			remapped.put(
					sigAsmRemapper.map(entry.getKey()),
					sigAsmRemapper.mapSignature(entry.getValue(), false)
			);
		}

		sigTinyRemapper.finish();
		return remapped;
	}
}
