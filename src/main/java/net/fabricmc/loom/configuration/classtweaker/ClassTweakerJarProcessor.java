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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.classtweaker.api.ClassTweaker;
import net.fabricmc.loom.api.processor.MinecraftJarProcessor;
import net.fabricmc.loom.api.processor.ProcessorContext;
import net.fabricmc.loom.api.processor.SpecContext;
import net.fabricmc.loom.util.fmj.FabricModJson;
import net.fabricmc.loom.util.fmj.ModEnvironment;

public abstract class ClassTweakerJarProcessor implements MinecraftJarProcessor<ClassTweakerJarProcessor.Spec> {
	private final ClassTweakerFactory classTweakerFactory;

	public ClassTweakerJarProcessor(ClassTweakerFactory classTweakerFactory) {
		this.classTweakerFactory = classTweakerFactory;
	}

	@Override
	public @Nullable ClassTweakerJarProcessor.Spec buildSpec(SpecContext context) {
		var classTweakers = new ArrayList<ClassTweakerEntry>();

		for (FabricModJson localMod : context.localMods()) {
			classTweakers.addAll(ClassTweakerEntry.readAll(localMod, true));
		}

		for (FabricModJson dependentMod : context.modDependencies()) {
			// TODO check and cache if it has any transitive CTs
			classTweakers.addAll(ClassTweakerEntry.readAll(dependentMod, false));
		}

		if (classTweakers.isEmpty()) {
			return null;
		}

		return new Spec(Collections.unmodifiableList(classTweakers));
	}

	public record Spec(List<ClassTweakerEntry> classTweakers) implements MinecraftJarProcessor.Spec {
		List<ClassTweakerEntry> getSupportedClassTweakers(ProcessorContext context) {
			return classTweakers.stream()
					.filter(entry -> isSupported(entry.environment(), context))
					.toList();
		}

		private static boolean isSupported(ModEnvironment modEnvironment, ProcessorContext context) {
			if (context.isMerged()) {
				// All envs are supported wth a merged jar
				return true;
			}

			if (context.includesClient() && modEnvironment.isClient()) {
				return true;
			}

			if (context.includesServer() && modEnvironment.isServer()) {
				return true;
			}

			// Universal supports all jars
			return modEnvironment == ModEnvironment.UNIVERSAL;
		}
	}

	@Override
	public void processJar(Path jar, Spec spec, ProcessorContext context) throws IOException {
		assert !spec.classTweakers.isEmpty();

		final List<ClassTweakerEntry> classTweakers = spec.getSupportedClassTweakers(context);
		final ClassTweaker classTweaker = classTweakerFactory.readEntries(classTweakers);
		classTweakerFactory.transformJar(jar, classTweaker);
	}
}
