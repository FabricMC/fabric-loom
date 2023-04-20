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

import java.util.function.Consumer;
import java.util.function.Predicate;

import org.gradle.api.artifacts.dsl.RepositoryHandler;

import net.fabricmc.loom.LoomRepositoryPlugin;
import net.fabricmc.loom.configuration.providers.minecraft.library.Library;
import net.fabricmc.loom.configuration.providers.minecraft.library.LibraryContext;
import net.fabricmc.loom.configuration.providers.minecraft.library.LibraryProcessor;
import net.fabricmc.loom.util.Platform;

/**
 * A processor to add support for ARM64.
 */
public class ArmNativesLibraryProcessor extends LibraryProcessor {
	private static final String LWJGL_GROUP = "org.lwjgl";

	public ArmNativesLibraryProcessor(Platform platform, LibraryContext context) {
		super(platform, context);
	}

	@Override
	public ApplicationResult getApplicationResult() {
		if (!context.usesLWJGL3()) {
			// Only supports LWJGL 3
			return ApplicationResult.DONT_APPLY;
		}

		if (!platform.getArchitecture().isArm() || !platform.getArchitecture().is64Bit()) {
			// Not an arm64 platform, can never apply this.
			return ApplicationResult.DONT_APPLY;
		}

		if (context.supportsArm64(platform.getOperatingSystem())) {
			// This version already supports arm64, nothing to do.
			return ApplicationResult.DONT_APPLY;
		}

		if (platform.getOperatingSystem().isMacOS()) {
			// Must upgrade natives to support macos ARM, even if not using classpath natives.
			return ApplicationResult.MUST_APPLY;
		}

		if (!context.hasClasspathNatives()) {
			// Only support updating linux and windows versions that have the natives on the classpath.
			return ApplicationResult.DONT_APPLY;
		}

		// Must add arm 64 to Windows and Linux for versions that use classpath natives.
		return ApplicationResult.MUST_APPLY;
	}

	@Override
	public Predicate<Library> apply(Consumer<Library> dependencyConsumer) {
		final String osName = switch (platform.getOperatingSystem()) {
		case MAC_OS -> "macos";
		case WINDOWS -> "windows";
		case LINUX -> "linux";
		};

		return library -> {
			if (library.is(LWJGL_GROUP) && library.target() == Library.Target.NATIVES && (library.classifier() != null && library.classifier().equals("natives-" + osName))) {
				// Add the arm64 natives.
				dependencyConsumer.accept(library.withClassifier(library.classifier() + "-arm64"));

				if (!context.hasClasspathNatives()) {
					// Remove the none arm64 natives when extracting.
					return false;
				}
			}

			return true;
		};
	}

	@Override
	public void applyRepositories(RepositoryHandler repositories) {
		LoomRepositoryPlugin.forceLWJGLFromMavenCentral(repositories);
	}
}
