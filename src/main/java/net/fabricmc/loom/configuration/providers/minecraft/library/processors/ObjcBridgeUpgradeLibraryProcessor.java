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

import net.fabricmc.loom.configuration.providers.minecraft.library.Library;
import net.fabricmc.loom.configuration.providers.minecraft.library.LibraryContext;
import net.fabricmc.loom.configuration.providers.minecraft.library.LibraryProcessor;
import net.fabricmc.loom.util.Platform;

public class ObjcBridgeUpgradeLibraryProcessor extends LibraryProcessor {
	private static final String OBJC_BRIDGE_PREFIX = "ca.weblite:java-objc-bridge";
	private static final String OBJC_BRIDGE_VERSION = "1.1";
	private static final String OBJC_BRIDGE_NAME = "%s:%s".formatted(OBJC_BRIDGE_PREFIX, OBJC_BRIDGE_VERSION);

	public ObjcBridgeUpgradeLibraryProcessor(Platform platform, LibraryContext context) {
		super(platform, context);
	}

	@Override
	public ApplicationResult getApplicationResult() {
		if (!context.usesLWJGL3()) {
			// Only supports LWJGL 3
			return ApplicationResult.DONT_APPLY;
		}

		if (!(platform.getOperatingSystem().isMacOS() && platform.getArchitecture().isArm())) {
			// Only supported on arm64 macOS
			return ApplicationResult.DONT_APPLY;
		}

		// Apply when Arm64 macOS is unsupported by Minecraft
		return context.supportsArm64(Platform.OperatingSystem.MAC_OS) ? ApplicationResult.DONT_APPLY : ApplicationResult.MUST_APPLY;
	}

	@Override
	public Predicate<Library> apply(Consumer<Library> dependencyConsumer) {
		// Add the updated library on the runtime classpath.
		dependencyConsumer.accept(Library.fromMaven(OBJC_BRIDGE_NAME, Library.Target.RUNTIME));

		return library -> {
			if (library.is(OBJC_BRIDGE_PREFIX)) {
				// Remove the natives, as they are included in the dep library we added to the runtime classpath above.
				return library.target() != Library.Target.NATIVES;
			}

			return true;
		};
	}
}
