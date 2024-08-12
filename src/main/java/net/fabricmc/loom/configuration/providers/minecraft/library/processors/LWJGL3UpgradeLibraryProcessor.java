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

public class LWJGL3UpgradeLibraryProcessor extends LibraryProcessor {
	private static final String LWJGL_GROUP = "org.lwjgl";
	// Version used to support ARM64 macOS, or Java 19 on all platforms
	private static final String LWJGL_VERSION = "3.3.2";
	// Version used to support RISC-V Linux
	private static final String LWJGL_VERSION_RISCV = "3.3.4";

	public LWJGL3UpgradeLibraryProcessor(Platform platform, LibraryContext context) {
		super(platform, context);
	}

	@Override
	public ApplicationResult getApplicationResult() {
		if (!context.usesLWJGL3()) {
			// Only supports LWJGL 3
			return ApplicationResult.DONT_APPLY;
		}

		if (context.isJava19OrLater() && !context.supportsJava19OrLater()) {
			// Update LWJGL when Java 19 is not supported
			return ApplicationResult.MUST_APPLY;
		}

		if (upgradeMacOSArm()) {
			// Update LWJGL when ARM64 macOS is not supported
			return ApplicationResult.MUST_APPLY;
		}

		if (upgradeLinuxRiscV()) {
			if (!context.hasClasspathNatives()) {
				// Don't support upgrading versions not using classpath natives to support RISC-V
				return ApplicationResult.DONT_APPLY;
			}

			// Update LWJGL when RISC-V Linux is not supported
			return ApplicationResult.MUST_APPLY;
		}

		// If the developer requests we can still upgrade LWJGL on this platform
		return ApplicationResult.CAN_APPLY;
	}

	@Override
	public Predicate<Library> apply(Consumer<Library> dependencyConsumer) {
		final String version = upgradeLinuxRiscV() ? LWJGL_VERSION_RISCV : LWJGL_VERSION;

		return library -> {
			if (library.is(LWJGL_GROUP) && library.name().startsWith("lwjgl")) {
				// Replace the natives with the new version, none natives become runtime only
				final Library.Target target = library.target() == Library.Target.NATIVES ? Library.Target.NATIVES : Library.Target.RUNTIME;
				final Library upgradedLibrary = library.withVersion(version).withTarget(target);
				dependencyConsumer.accept(upgradedLibrary);
			}

			return true;
		};
	}

	@Override
	public void applyRepositories(RepositoryHandler repositories) {
		LoomRepositoryPlugin.forceLWJGLFromMavenCentral(repositories);
	}

	// Add support for macOS
	private boolean upgradeMacOSArm() {
		return platform.getOperatingSystem().isMacOS()
				&& platform.getArchitecture().isArm()
				&& !context.supportsArm64(Platform.OperatingSystem.MAC_OS)
				&& !context.hasClasspathNatives();
	}

	// Add support for Linux RISC-V
	private boolean upgradeLinuxRiscV() {
		return platform.getOperatingSystem().isLinux()
				&& platform.getArchitecture().isRiscV()
				&& platform.getArchitecture().is64Bit();
	}
}
