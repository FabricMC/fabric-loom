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

package net.fabricmc.loom.api.processor;

import java.io.IOException;
import java.nio.file.Path;

import org.gradle.api.Named;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.mappingio.tree.MemoryMappingTree;

public interface MinecraftJarProcessor<S extends MinecraftJarProcessor.Spec> extends Named {
	@Nullable
	S buildSpec(SpecContext context);

	void processJar(Path jar, S spec, ProcessorContext context) throws IOException;

	@Nullable
	default MappingsProcessor<S> processMappings() {
		return null;
	}

	interface Spec {
		// Must make sure hashCode is correctly implemented.
	}

	interface MappingsProcessor<S> {
		boolean transform(MemoryMappingTree mappings, S spec, MappingProcessorContext context);
	}
}
