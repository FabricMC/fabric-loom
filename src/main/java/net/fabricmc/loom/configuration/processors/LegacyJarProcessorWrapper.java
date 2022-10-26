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

package net.fabricmc.loom.configuration.processors;

import java.io.IOException;
import java.nio.file.Path;

import javax.inject.Inject;

import org.gradle.api.Project;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.api.processor.MinecraftJarProcessor;
import net.fabricmc.loom.api.processor.ProcessorContext;
import net.fabricmc.loom.api.processor.SpecContext;

/**
 * Wrapper around the deprecated API.
 */
public abstract class LegacyJarProcessorWrapper implements MinecraftJarProcessor<LegacyJarProcessorWrapper.Spec> {
	private final JarProcessor delegate;

	@Inject
	public LegacyJarProcessorWrapper(JarProcessor delegate) {
		this.delegate = delegate;
	}

	@Inject
	public abstract Project getProject();

	@Override
	public String getName() {
		return "legacy:" + delegate.getClass().getCanonicalName();
	}

	@Override
	public @Nullable LegacyJarProcessorWrapper.Spec buildSpec(SpecContext context) {
		delegate.setup();
		return new Spec(delegate.getId());
	}

	@Override
	public void processJar(Path jar, Spec spec, ProcessorContext context) throws IOException {
		delegate.process(jar.toFile());
	}

	public record Spec(String cacheValue) implements MinecraftJarProcessor.Spec {
	}
}
