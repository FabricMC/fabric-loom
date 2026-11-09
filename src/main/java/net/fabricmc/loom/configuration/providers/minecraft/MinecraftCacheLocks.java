/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2026 FabricMC
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
import java.nio.file.Path;

/// Coordinates shared Minecraft files across Loom classloaders in the current JVM.
public final class MinecraftCacheLocks {
	private static final Object GLOBAL_LOCK = "fabric.loom.internal.global.lock".intern();

	private MinecraftCacheLocks() {
	}

	/// Serializes downloads and shared version-manifest access.
	public static Object getGlobalLock() {
		return GLOBAL_LOCK;
	}

	/// Coordinates projects using the same physical Minecraft cache directory.
	/// Acquire this lock before the global lock when both are needed.
	public static Object getCacheLock(Path cacheDirectory) throws IOException {
		String path = cacheDirectory.toFile().getCanonicalPath();
		return ("fabric.loom.internal.minecraft.cache.lock:" + path).intern();
	}
}
