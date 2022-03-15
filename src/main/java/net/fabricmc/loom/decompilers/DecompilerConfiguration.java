/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2018-2022 FabricMC
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

package net.fabricmc.loom.decompilers;

import org.gradle.api.Project;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.decompilers.LoomDecompiler;
import net.fabricmc.loom.decompilers.cfr.LoomCFRDecompiler;
import net.fabricmc.loom.decompilers.fernflower.FabricFernFlowerDecompiler;

public final class DecompilerConfiguration {
	public static final String FERN_FLOWER = "fernFlower";
	public static final String CFR = "cfr";
	public static final String DEFAULT = CFR;

	private DecompilerConfiguration() {
	}

	public static void setup(Project project) {
		registerDecompiler(project, FERN_FLOWER, FabricFernFlowerDecompiler.class);
		registerDecompiler(project, CFR, LoomCFRDecompiler.class);
	}

	private static void registerDecompiler(Project project, String name, Class<? extends LoomDecompiler> decompilerClass) {
		LoomGradleExtension.get(project).getDecompilerOptions().register(name, options -> options.getDecompilerClassName().set(decompilerClass.getName()));
	}
}
