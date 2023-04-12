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

package net.fabricmc.loom.configuration.providers.minecraft.library.processors;

import java.util.List;

import org.gradle.api.artifacts.dsl.RepositoryHandler;

import net.fabricmc.loom.LoomRepositoryPlugin;
import net.fabricmc.loom.configuration.providers.minecraft.library.LibraryContext;
import net.fabricmc.loom.configuration.providers.minecraft.library.LibraryProcessor;
import net.fabricmc.loom.util.Platform;

/**
 * 1.4.7 contains an LWJGL version with an invalid maven pom, set the metadata sources to not use the pom for this version.
 */
public class LWJGL2MavenLibraryProcessor extends LibraryProcessor {
	private static final List<String> LWJGL_LIST = List.of(
			"org.lwjgl.lwjgl:lwjgl:2.9.1-nightly-20130708-debug3",
			"org.lwjgl.lwjgl:lwjgl:2.9.1-nightly-20131017"
	);

	public LWJGL2MavenLibraryProcessor(Platform platform, LibraryContext context) {
		super(platform, context);
	}

	@Override
	public ApplicationResult getApplicationResult() {
		if (context.usesLWJGL3()) {
			// Does not support LWJGL 3
			return ApplicationResult.DONT_APPLY;
		}

		return LWJGL_LIST.stream().anyMatch(context::hasLibrary) ? ApplicationResult.MUST_APPLY : ApplicationResult.DONT_APPLY;
	}

	@Override
	public void applyRepositories(RepositoryHandler repositories) {
		LoomRepositoryPlugin.setupForLegacyVersions(repositories);
	}
}
